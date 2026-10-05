package io.github.jorgetroya80.madmobility.shared.emt

import io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt.EmtStation
import io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt.EmtStationMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * SC15 and SC13: real EMT login and one BiciMAD call through the production beans, checking the
 * response still matches the fixtures in src/test/resources/emt and that [EmtStationMapper]
 * translates enough of it. The stations are fetched once per class: two EMT calls per run.
 * Manual only: `./gradlew smokeTest` with api/.env.local.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmtSmokeTest(
    @Autowired private val auth: EmtAuth,
    @Autowired private val client: EmtHttpClient,
    @Autowired private val jsonMapper: JsonMapper,
) {
    private val rawStations: List<Map<*, *>> by lazy { client.get("bicimad", STATIONS, Map::class.java) }

    @Test
    fun `logs in with a token valid for about a day`() {
        auth.accessToken()
        val renewsAt = requireNotNull(auth.tokenRenewsAt())

        assertThat(Duration.between(Instant.now(), renewsAt)).isBetween(Duration.ofHours(23), Duration.ofHours(24))
        log.info("EMT smoke test: token renews at {}", renewsAt)
    }

    @Test
    fun `lists BiciMAD stations shaped like the fixtures`() {
        val fixtureStation = JsonMapper().readTree(File(FIXTURE)).path("data").first()
        val fixtureKeys = fixtureStation.propertyNames().asSequence().toSet()

        assertThat(rawStations).hasSizeGreaterThan(MIN_VALID_STATIONS)
        assertThat(rawStations).allSatisfy { station -> assertThat(station.keys).containsExactlyInAnyOrderElementsOf(fixtureKeys) }
        assertThat(rawStations.map { it["light"] }).allSatisfy { assertThat(it).isIn(0, 1, 2, 3) }
        log.info("EMT smoke test: {} raw stations shaped like the fixtures", rawStations.size)
    }

    @Test
    fun `translates more than 600 valid BiciMAD stations`() {
        val emtStations = rawStations.map { jsonMapper.convertValue(it, EmtStation::class.java) }

        val validStations = EmtStationMapper.toDomain(emtStations)

        log.info(
            "EMT smoke test: {} valid stations, {} discarded of {}",
            validStations.size,
            emtStations.size - validStations.size,
            emtStations.size,
        )
        assertThat(validStations).hasSizeGreaterThan(MIN_VALID_STATIONS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmtSmokeTest::class.java)
        const val STATIONS = "/v1/transport/bicimad/stations/"
        const val FIXTURE = "src/test/resources/emt/bicimad-stations.json"
        const val MIN_VALID_STATIONS = 600

        @JvmStatic
        @BeforeAll
        fun requireCredentials() {
            assumeTrue(
                File(".env.local").exists() || !System.getenv("EMT_EMAIL").isNullOrBlank() ||
                    !System.getenv("EMT_CLIENT_ID").isNullOrBlank(),
                "No EMT credentials (api/.env.local or EMT_* variables): smoke test skipped",
            )
        }
    }
}
