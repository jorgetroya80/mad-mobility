package io.github.jorgetroya80.madmobility.modules.bicimad.http

import io.github.jorgetroya80.madmobility.modules.bicimad.application.FindNearbyStations
import io.github.jorgetroya80.madmobility.modules.bicimad.application.GetStation
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.GeoPoint
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import io.github.jorgetroya80.madmobility.shared.web.RequestIdFilter
import io.github.resilience4j.circuitbreaker.CircuitBreaker
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant

@WebMvcTest(StationsController::class)
@Import(FindNearbyStations::class, GetStation::class, StationsControllerTest.Config::class)
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
    fun `carries freshness and source with an ISO 8601 UTC timestamp in whole seconds`() {
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

    @Test
    fun `near returns only stations within the radius, nearest first, with whole-meter distances`() {
        given(station("1", latOffset = FAR), station("2", latOffset = MIDDLE), station("3", latOffset = NEAR))

        val stations = stationsOf("$STATIONS?near=40.4168,-3.7038&radius=500")

        assertThat(stations.map { it.path("number").asString() }).containsExactly("3", "2")
        assertThat(stations.map { it.path("distanceMeters").isInt }).containsOnly(true)
        assertThat(stations.map { it.path("distanceMeters").asInt() }).isSorted().allSatisfy { assertThat(it).isBetween(0, 500) }
    }

    @Test
    fun `near without radius uses 500 meters`() {
        given(station("1", latOffset = BEYOND_DEFAULT_RADIUS), station("2", latOffset = MIDDLE))

        val stations = stationsOf("$STATIONS?near=40.4168,-3.7038")

        assertThat(stations.map { it.path("number").asString() }).containsExactly("2")
    }

    @Test
    fun `need docks puts stations with free docks first and keeps every station`() {
        given(
            station("1", latOffset = NEAR, freeDocks = 0),
            station("2", latOffset = MIDDLE, freeDocks = 3),
            station("3", latOffset = NEAR * 2, freeDocks = 0, status = StationStatus.NO_SERVICE),
        )

        val withNeed = stationsOf("$STATIONS?near=40.4168,-3.7038&need=docks")
        val withoutNeed = stationsOf("$STATIONS?near=40.4168,-3.7038")

        assertThat(withNeed.map { it.path("number").asString() }).containsExactly("2", "1", "3")
        assertThat(withNeed).hasSameSizeAs(withoutNeed)
    }

    @Test
    fun `need bikes without near orders available stations first, then by number`() {
        given(station("1", bikes = 0), station("10", bikes = 2), station("5", bikes = 1))

        val stations = stationsOf("$STATIONS?need=bikes")

        assertThat(stations.map { it.path("number").asString() }).containsExactly("5", "10", "1")
    }

    @Test
    fun `without near there is no distanceMeters`() {
        given(station("1"))

        val station = stationsOf(STATIONS).single()

        assertThat(station.has("distanceMeters")).isFalse()
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "radius=6000&near=40.4168,-3.7038",
            "radius=0&near=40.4168,-3.7038",
            "radius=abc&near=40.4168,-3.7038",
            "radius=500",
            "near=abc",
            "near=40.4168",
            "near=40.4168,-3.7038,1",
            "near=91,0",
            "near=0,-181",
            "near=NaN,0",
            "need=car",
            "need=BIKES",
        ],
    )
    fun `invalid query is a 400 problem with the request id`(query: String) {
        given(station("1"))

        val result =
            mvc
                .get()
                .uri("$STATIONS?$query")
                .header(RequestIdFilter.HEADER, "req-400")
                .exchange()

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-400")
    }

    @Test
    fun `station by id has the station, freshness in whole seconds and source, without distanceMeters`() {
        every { provider.snapshot() } returns StationSnapshot(listOf(FUENCARRAL), UPDATED_AT, stale = true)

        val result = mvc.get().uri("$STATIONS/1409").exchange()

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        val body = json(result.response.contentAsString)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("station", "updatedAt", "stale", "source")
        assertThat(body.path("updatedAt").asString()).isEqualTo("2026-10-05T10:15:00Z")
        assertThat(body.path("stale").asBoolean()).isTrue()
        assertThat(body.path("source").asString()).isEqualTo("EMT Madrid MobilityLabs")
        assertThat(body.path("station")).isEqualTo(json(STATION_JSON))
    }

    @Test
    fun `unknown station id is a 404 problem with the request id`() {
        given(station("1"))

        val result =
            mvc
                .get()
                .uri("$STATIONS/999999")
                .header(RequestIdFilter.HEADER, "req-404")
                .exchange()

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-404")
    }

    @Test
    fun `non-numeric station id is a 400 problem with the request id`() {
        val result =
            mvc
                .get()
                .uri("$STATIONS/abc")
                .header(RequestIdFilter.HEADER, "req-400")
                .exchange()

        assertThat(result).hasStatus(400).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(json(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-400")
    }

    @Test
    fun `revalidating with the received ETag is a 304 with no-cache`() {
        given(station("1"))
        val etag =
            checkNotNull(
                mvc
                    .get()
                    .uri(STATIONS)
                    .exchange()
                    .response
                    .getHeader(HttpHeaders.ETAG),
            )

        val result =
            mvc
                .get()
                .uri(STATIONS)
                .header(HttpHeaders.IF_NONE_MATCH, etag)
                .exchange()

        assertThat(result).hasStatus(304).hasHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        assertThat(result.response.contentAsByteArray).isEmpty()
    }

    private fun given(vararg stations: Station) {
        every { provider.snapshot() } returns StationSnapshot(stations.toList(), UPDATED_AT, stale = false)
    }

    private fun stationsOf(uri: String): List<JsonNode> {
        val result = mvc.get().uri(uri).exchange()
        assertThat(result).hasStatusOk()
        return json(result.response.contentAsString).path("stations").values().toList()
    }

    private fun station(
        number: String,
        latOffset: Double = 0.0,
        bikes: Int = 1,
        freeDocks: Int = 1,
        status: StationStatus = StationStatus.OPERATIONAL,
    ) = FUENCARRAL.copy(
        id = number.hashCode(),
        number = number,
        location = GeoPoint(SOL_LAT + latOffset, SOL_LON),
        bikes = bikes,
        freeDocks = freeDocks,
        status = status,
    )

    private fun json(body: String): JsonNode = JsonMapper().readTree(body)

    private companion object {
        const val STATIONS = "/v1/bicimad/stations"
        val UPDATED_AT: Instant = Instant.parse("2026-10-05T10:15:00.123456Z")
        val STATION_JSON =
            """
            {"id":1409,"number":"5","name":"Fuencarral","address":"Calle Fuencarral nº 106","lat":40.4285212,"lon":-3.7021354,
             "status":"OPERATIONAL","bikes":2,"freeDocks":23,"totalDocks":27,"occupancy":"LOW"}
            """.trimIndent()
        const val SOL_LAT = 40.4168
        const val SOL_LON = -3.7038

        // Degrees of latitude north of Sol: about 111 m, 334 m, 556 m and 1,112 m
        const val NEAR = 0.001
        const val MIDDLE = 0.003
        const val BEYOND_DEFAULT_RADIUS = 0.005
        const val FAR = 0.01
        val FUENCARRAL =
            Station(
                id = 1409,
                number = "5",
                name = "Fuencarral",
                address = "Calle Fuencarral nº 106",
                location = GeoPoint(40.4285212, -3.7021354),
                bikes = 2,
                freeDocks = 23,
                totalDocks = 27,
                status = StationStatus.OPERATIONAL,
                occupancy = Occupancy.LOW,
            )
    }
}
