package io.github.jorgetroya80.madtransit.modules.bus.http

import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtArrivalMapper
import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtArrivesData
import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtBusStop
import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtBusStopMapper
import io.github.jorgetroya80.madtransit.modules.bus.adapters.emt.EmtLineInfo
import io.github.jorgetroya80.madtransit.modules.bus.application.FindNearbyStops
import io.github.jorgetroya80.madtransit.modules.bus.application.GetArrivals
import io.github.jorgetroya80.madtransit.modules.bus.application.GetStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusLine
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetail
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetailSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopIndex
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.ArrivalProvider
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopDetailProvider
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
@Import(FindNearbyStops::class, GetStop::class, GetArrivals::class, BusStopsControllerTest.Config::class)
class BusStopsControllerTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val provider: BusStopProvider,
    @Autowired private val detailProvider: BusStopDetailProvider,
    @Autowired private val arrivalProvider: ArrivalProvider,
) {
    @TestConfiguration
    class Config {
        @Bean
        fun busStopProvider(): BusStopProvider = mockk()

        @Bean
        fun busStopDetailProvider(): BusStopDetailProvider = mockk()

        @Bean
        fun arrivalProvider(): ArrivalProvider = mockk()

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
        every { provider.snapshot() } returns BusStopSnapshot(BusStopIndex(fixtureStops()), UPDATED_AT, stale = true)

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

    @Test
    fun `SC2 stop by number has the list entry, the address and today's lines, as in the spec`() {
        given(fixtureStops())
        givenDetail("72", DETAIL_72)

        val result = mvc.get().uri("$STOPS/72").exchange()

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        val body = json(result.response.contentAsString)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("stop", "updatedAt", "stale", "source")
        assertThat(body.path("updatedAt").asString()).isEqualTo("2026-10-30T10:15:00Z")
        assertThat(body.path("source").asString()).isEqualTo("EMT Madrid MobilityLabs")
        val stop = body.path("stop")
        assertThat(stop.propertyNames()).containsExactlyInAnyOrder("number", "name", "address", "lat", "lon", "lines")
        assertThat(stop.path("number").asString()).isEqualTo("72")
        assertThat(stop.path("name").asString()).isEqualTo("Cibeles-Casa de América")
        assertThat(stop.path("address").asString()).isEqualTo("Pº de Recoletos, 2 (Pza. de Cibeles)")
        assertThat(stop.path("lat").asDouble()).isEqualTo(40.420268054530624)
        assertThat(stop.path("lon").asDouble()).isEqualTo(-3.692139136326868)
        val line = stop.path("lines").values().first()
        assertThat(line.propertyNames()).containsExactlyInAnyOrder(*LINE_PROPERTIES)
        assertThat(line.path("label").asString()).isEqualTo("5")
        assertThat(line.path("headerA").asString()).isEqualTo("SOL/SEVILLA")
        assertThat(line.path("headerB").asString()).isEqualTo("CHAMARTIN")
        assertThat(line.path("direction").asString()).isEqualTo("B")
        assertThat(line.path("firstBus").asString()).isEqualTo("06:30")
        assertThat(line.path("lastBus").asString()).isEqualTo("22:58")
        assertThat(line.path("minFrequencyMinutes").asInt()).isEqualTo(10)
        assertThat(line.path("maxFrequencyMinutes").asInt()).isEqualTo(22)
    }

    @Test
    fun `a line without today's schedule sends its schedule and frequencies as null`() {
        given(fixtureStops())
        givenDetail(
            "72",
            BusStopDetail(address = null, lines = listOf(BusLine("5", "SOL/SEVILLA", "CHAMARTIN", "B", null, null, null, null))),
        )

        val body =
            json(
                mvc
                    .get()
                    .uri("$STOPS/72")
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.at("/stop/address").isNull).isTrue()
        val line = body.at("/stop/lines/0")
        assertThat(line.propertyNames()).containsExactlyInAnyOrder(*LINE_PROPERTIES)
        listOf("firstBus", "lastBus", "minFrequencyMinutes", "maxFrequencyMinutes").forEach {
            assertThat(line.path(it).isNull).describedAs(it).isTrue()
        }
    }

    @Test
    fun `stop by number carries the stale flag`() {
        given(fixtureStops())
        every { detailProvider.detail("72") } returns BusStopDetailSnapshot(DETAIL_72, UPDATED_AT, stale = true)

        val body =
            json(
                mvc
                    .get()
                    .uri("$STOPS/72")
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.path("stale").asBoolean()).isTrue()
    }

    @Test
    fun `unknown stop number is a 404 problem with the request id`() {
        given(fixtureStops())

        val result =
            mvc
                .get()
                .uri("$STOPS/99999999")
                .header(RequestIdFilter.HEADER, "req-404")
                .exchange()

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-404")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["abc", "72a", "-72", "7 2"])
    fun `non-numeric stop number is a 400 problem with the request id`(number: String) {
        given(fixtureStops())

        val result =
            mvc
                .get()
                .uri("$STOPS/{number}", number)
                .header(RequestIdFilter.HEADER, "req-400")
                .exchange()

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-400")
    }

    @Test
    fun `SC3 arrivals have the spec fields, soonest first, with no estimate as nulls`() {
        given(fixtureStops())
        givenArrivals(fixtureArrivals())

        val result = mvc.get().uri("$STOPS/72/arrivals").exchange()

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        val body = json(result.response.contentAsString)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("arrivals", "updatedAt", "stale", "source")
        assertThat(body.path("updatedAt").asString()).isEqualTo("2026-10-30T10:15:00Z")
        assertThat(body.path("stale").asBoolean()).isFalse()
        assertThat(body.path("source").asString()).isEqualTo("EMT Madrid MobilityLabs")
        val arrivals = body.path("arrivals").values().toList()
        assertThat(arrivals.map { it.path("line").asString() }).containsExactly("14", "45", "37", "53", "5", "C03")
        arrivals.forEach { assertThat(it.propertyNames()).containsExactlyInAnyOrder(*ARRIVAL_PROPERTIES) }
        val first = arrivals.first()
        assertThat(first.path("destination").asString()).isEqualTo("PIO XII")
        assertThat(first.path("minutes").asInt()).isEqualTo(0)
        assertThat(first.path("noEstimate").asBoolean()).isFalse()
        assertThat(first.path("distanceMeters").asInt()).isEqualTo(0)
        assertThat(first.path("busLat").asDouble()).isEqualTo(40.41441008140411)
        assertThat(first.path("busLon").asDouble()).isEqualTo(-3.6930564972057955)
        assertThat(arrivals.map { it.path("minutes").asInt() }.dropLast(1)).containsExactly(0, 0, 2, 23, 25)
        val noEstimate = arrivals.last()
        assertThat(noEstimate.path("noEstimate").asBoolean()).isTrue()
        listOf("minutes", "distanceMeters", "busLat", "busLon").forEach {
            assertThat(noEstimate.path(it).isNull).describedAs(it).isTrue()
        }
    }

    @Test
    fun `arrivals carry the stale flag`() {
        given(fixtureStops())
        every { arrivalProvider.arrivals("72") } returns ArrivalSnapshot(fixtureArrivals(), UPDATED_AT, stale = true)

        val body =
            json(
                mvc
                    .get()
                    .uri("$STOPS/72/arrivals")
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.path("stale").asBoolean()).isTrue()
    }

    @Test
    fun `no bus coming gives an empty list of arrivals`() {
        given(fixtureStops())
        givenArrivals(emptyList())

        val result = mvc.get().uri("$STOPS/72/arrivals").exchange()

        assertThat(result).hasStatusOk()
        val arrivals = json(result.response.contentAsString).path("arrivals")
        assertThat(arrivals.isArray).isTrue()
        assertThat(arrivals).isEmpty()
    }

    @Test
    fun `arrivals of an unknown stop number are a 404 problem with the request id`() {
        given(fixtureStops())

        val result =
            mvc
                .get()
                .uri("$STOPS/99999999/arrivals")
                .header(RequestIdFilter.HEADER, "req-404")
                .exchange()

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-404")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["abc", "72a", "-72", "7 2"])
    fun `arrivals of a non-numeric stop number are a 400 problem with the request id`(number: String) {
        given(fixtureStops())

        val result =
            mvc
                .get()
                .uri("$STOPS/{number}/arrivals", number)
                .header(RequestIdFilter.HEADER, "req-400")
                .exchange()

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-400")
    }

    private fun givenArrivals(arrivals: List<Arrival>) {
        every { arrivalProvider.arrivals("72") } returns ArrivalSnapshot(arrivals, UPDATED_AT, stale = false)
    }

    private fun fixtureArrivals() =
        EmtArrivalMapper.toDomain(
            "72",
            readFixture<EmtArrivesData>("/emt/bus-arrivals.json").single().arrive.orEmpty(),
        )

    private fun givenDetail(
        number: String,
        detail: BusStopDetail,
    ) {
        every { detailProvider.detail(number) } returns BusStopDetailSnapshot(detail, UPDATED_AT, stale = false)
    }

    private fun given(stops: List<BusStop>) {
        every { provider.snapshot() } returns BusStopSnapshot(BusStopIndex(stops), UPDATED_AT, stale = false)
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
        val ARRIVAL_PROPERTIES =
            arrayOf("line", "destination", "minutes", "noEstimate", "distanceMeters", "busLat", "busLon")
        val LINE_PROPERTIES =
            arrayOf("label", "headerA", "headerB", "direction", "firstBus", "lastBus", "minFrequencyMinutes", "maxFrequencyMinutes")
        val DETAIL_72 =
            BusStopDetail(
                address = "Pº de Recoletos, 2 (Pza. de Cibeles)",
                lines = listOf(BusLine("5", "SOL/SEVILLA", "CHAMARTIN", "B", "06:30", "22:58", 10, 22)),
            )
    }
}
