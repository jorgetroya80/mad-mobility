package io.github.jorgetroya80.madmobility.modules.bicimad.http

import io.github.jorgetroya80.madmobility.modules.bicimad.application.ListStations
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant

@WebMvcTest(StationsController::class)
@Import(ListStations::class, StationsControllerTest.Config::class)
class StationsControllerTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val provider: StationProvider,
) {
    @TestConfiguration
    class Config {
        @Bean
        fun stationProvider(): StationProvider = mockk()

        // Needed by the shared ProblemDetailsHandler, which the web slice loads
        @Bean
        fun clock(): Clock = Clock.systemUTC()

        @Bean
        fun emtCircuitBreaker(): CircuitBreaker = CircuitBreaker.ofDefaults("test")
    }

    @Test
    fun `lists every station with the spec fields`() {
        every { provider.snapshot() } returns StationSnapshot(listOf(FUENCARRAL), UPDATED_AT, stale = false)

        val result = mvc.get().uri(STATIONS).exchange()

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        val station = json(result.response.contentAsString).path("stations").single()
        assertThat(station.propertyNames()).containsExactlyInAnyOrder(
            "id",
            "number",
            "name",
            "address",
            "lat",
            "lon",
            "status",
            "bikes",
            "freeDocks",
            "totalDocks",
            "occupancy",
        )
        assertThat(station.path("id").asInt()).isEqualTo(1409)
        assertThat(station.path("number").asString()).isEqualTo("5")
        assertThat(station.path("name").asString()).isEqualTo("Fuencarral")
        assertThat(station.path("address").asString()).isEqualTo("Calle Fuencarral nº 106")
        assertThat(station.path("lat").asDouble()).isEqualTo(40.4285212)
        assertThat(station.path("lon").asDouble()).isEqualTo(-3.7021354)
        assertThat(station.path("status").asString()).isEqualTo("OPERATIONAL")
        assertThat(station.path("bikes").asInt()).isEqualTo(2)
        assertThat(station.path("freeDocks").asInt()).isEqualTo(23)
        assertThat(station.path("totalDocks").asInt()).isEqualTo(27)
        assertThat(station.path("occupancy").asString()).isEqualTo("LOW")
    }

    @Test
    fun `carries freshness and source with an ISO 8601 UTC timestamp`() {
        every { provider.snapshot() } returns StationSnapshot(listOf(FUENCARRAL), UPDATED_AT, stale = true)

        val body =
            json(
                mvc
                    .get()
                    .uri(STATIONS)
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.propertyNames()).containsExactlyInAnyOrder("stations", "updatedAt", "stale", "source")
        assertThat(body.path("updatedAt").asString()).isEqualTo("2026-10-05T10:15:00Z")
        assertThat(body.path("stale").asBoolean()).isTrue()
        assertThat(body.path("source").asString()).isEqualTo("EMT Madrid MobilityLabs")
    }

    @Test
    fun `orders stations by number, numeric part first`() {
        val stations = listOf("10", "5b", "5", "5a").map { FUENCARRAL.copy(number = it) }
        every { provider.snapshot() } returns StationSnapshot(stations, UPDATED_AT, stale = false)

        val body =
            json(
                mvc
                    .get()
                    .uri(STATIONS)
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.path("stations").values().map { it.path("number").asString() }).containsExactly("5", "5a", "5b", "10")
    }

    @Test
    fun `no station gives an empty list`() {
        every { provider.snapshot() } returns StationSnapshot(emptyList(), UPDATED_AT, stale = false)

        val body =
            json(
                mvc
                    .get()
                    .uri(STATIONS)
                    .exchange()
                    .response.contentAsString,
            )

        assertThat(body.path("stations").isArray).isTrue()
        assertThat(body.path("stations")).isEmpty()
    }

    @Test
    fun `response has no EMT field names`() {
        every { provider.snapshot() } returns StationSnapshot(listOf(FUENCARRAL), UPDATED_AT, stale = false)

        val body =
            mvc
                .get()
                .uri(STATIONS)
                .exchange()
                .response.contentAsString

        assertThat(body).doesNotContain(
            "dock_bikes",
            "free_bases",
            "total_bases",
            "activate",
            "no_available",
            "light",
            "virtualDelete",
            "geometry",
            "coordinates",
        )
    }

    private fun json(body: String): JsonNode = JsonMapper().readTree(body)

    private companion object {
        const val STATIONS = "/v1/bicimad/stations"
        val UPDATED_AT: Instant = Instant.parse("2026-10-05T10:15:00Z")
        val FUENCARRAL =
            Station(
                id = 1409,
                number = "5",
                name = "Fuencarral",
                address = "Calle Fuencarral nº 106",
                lat = 40.4285212,
                lon = -3.7021354,
                bikes = 2,
                freeDocks = 23,
                totalDocks = 27,
                status = StationStatus.OPERATIONAL,
                occupancy = Occupancy.LOW,
            )
    }
}
