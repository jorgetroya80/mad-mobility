package io.github.jorgetroya80.madtransit.modules.bus.http

import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtBusStop
import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtBusStopMapper
import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtLineInfo
import io.github.jorgetroya80.madtransit.modules.bus.application.FindNearbyStops
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import io.github.jorgetroya80.madtransit.shared.emt.EmtResponse
import io.github.jorgetroya80.madtransit.shared.web.RequestIdFilter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Clock
import java.time.Instant

@WebMvcTest(BusStopsController::class)
@Import(FindNearbyStops::class, BusStopsControllerTest.Config::class)
class BusStopsControllerTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val provider: BusStopProvider,
) {
    @TestConfiguration
    class Config {
        @Bean
        fun busStopProvider(): BusStopProvider = mockk()

        // Needed by the shared ProblemDetailsHandler, which the web slice loads
        @Bean
        fun clock(): Clock = Clock.systemUTC()

        // Needed by the shared RateLimitFilter, which the web slice loads
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
    }

    @Test
    fun `SC1 near Puerta del Sol returns the fixture stops within 500 meters, nearest first, with their lines`() {
        given(fixtureStops())

        val stops = stopsOf("$STOPS?near=40.4168,-3.7038")

        assertThat(stops.map { it.path("number").asString() }).containsExactly("2115", "4025")
        assertThat(stops.map { it.path("distanceMeters").asInt() }).containsExactly(240, 248)
        assertThat(stops.first().path("name").asString()).isEqualTo("Plaza Santa Cruz-Plaza Mayor")
        assertThat(stops.first().path("lat").asDouble()).isEqualTo(40.41513710448409)
        assertThat(stops.first().path("lon").asDouble()).isEqualTo(-3.705616121396342)
        assertThat(
            stops
                .first()
                .path("lines")
                .values()
                .map { it.asString() },
        ).containsExactly("M3")
        assertThat(
            stops
                .last()
                .path("lines")
                .values()
                .map { it.asString() },
        ).containsExactly("002", "M1", "M3")
    }

    @Test
    fun `radius widens the search up to 2000 meters`() {
        given(fixtureStops())

        val stops = stopsOf("$STOPS?near=40.4168,-3.7038&radius=2000")

        assertThat(stops.map { it.path("number").asString() }).containsExactly("2115", "4025", "72")
        assertThat(stops.last().path("distanceMeters").asInt()).isEqualTo(1060)
    }

    @Test
    fun `radius 1 is valid`() {
        given(fixtureStops())

        assertThat(stopsOf("$STOPS?near=40.4168,-3.7038&radius=1")).isEmpty()
    }

    @Test
    fun `without near every stop is listed by number with the spec fields and no distanceMeters`() {
        given(fixtureStops())

        val stops = stopsOf(STOPS)

        assertThat(stops.map { it.path("number").asString() }).containsExactly("1", "72", "2115", "2768", "4025")
        stops.forEach { assertThat(it.propertyNames()).containsExactlyInAnyOrder("number", "name", "lat", "lon", "lines") }
    }

    @Test
    fun `carries freshness and source with an ISO 8601 UTC timestamp in whole seconds`() {
        every { provider.snapshot() } returns BusStopSnapshot(fixtureStops(), UPDATED_AT, stale = true)

        val body =
            json(
                mvc
                    .get()
                    .uri(STOPS)
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.propertyNames()).containsExactlyInAnyOrder("stops", "updatedAt", "stale", "source")
        assertThat(body.path("updatedAt").asString()).isEqualTo("2026-10-30T10:15:00Z")
        assertThat(body.path("stale").asBoolean()).isTrue()
        assertThat(body.path("source").asString()).isEqualTo("EMT Madrid MobilityLabs")
    }

    @Test
    fun `no stop gives an empty list`() {
        given(emptyList())

        val result = mvc.get().uri(STOPS).exchange()

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        val stops = json(result.response.contentAsString).path("stops")
        assertThat(stops.isArray).isTrue()
        assertThat(stops).isEmpty()
    }

    @Test
    fun `response has no EMT field names or internal line codes`() {
        given(fixtureStops())

        val body =
            mvc
                .get()
                .uri(STOPS)
                .exchange()
                .response.contentAsString

        assertThat(body).doesNotContain("node", "geometry", "coordinates", "wifi", "603/2")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "radius=500",
            "radius=0&near=40.4168,-3.7038",
            "radius=2001&near=40.4168,-3.7038",
            "radius=abc&near=40.4168,-3.7038",
            "near=abc",
            "near=40.4168",
            "near=40.4168,-3.7038,1",
            "near=91,0",
            "near=0,-181",
            "near=NaN,0",
        ],
    )
    fun `invalid query is a 400 problem with the request id`(query: String) {
        given(fixtureStops())

        val result =
            mvc
                .get()
                .uri("$STOPS?$query")
                .header(RequestIdFilter.HEADER, "req-400")
                .exchange()

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-400")
    }

    private fun given(stops: List<BusStop>) {
        every { provider.snapshot() } returns BusStopSnapshot(stops, UPDATED_AT, stale = false)
    }

    private fun stopsOf(uri: String): List<JsonNode> {
        val result = mvc.get().uri(uri).exchange()
        assertThat(result).hasStatusOk()
        return json(result.response.contentAsString).path("stops").values().toList()
    }

    private fun fixtureStops(): List<BusStop> =
        EmtBusStopMapper.toDomain(readFixture<EmtBusStop>("/emt/bus-stops-list.json"), readFixture<EmtLineInfo>("/emt/bus-lines-info.json"))

    private inline fun <reified T> readFixture(path: String): List<T> {
        val json = requireNotNull(javaClass.getResource(path)) { "missing fixture $path" }.readText()
        return jacksonObjectMapper().readValue<EmtResponse<T>>(json).data.orEmpty()
    }

    private fun json(body: String): JsonNode = JsonMapper().readTree(body)

    private companion object {
        const val STOPS = "/v1/bus/stops"
        val UPDATED_AT: Instant = Instant.parse("2026-10-30T10:15:00.123456Z")
    }
}
