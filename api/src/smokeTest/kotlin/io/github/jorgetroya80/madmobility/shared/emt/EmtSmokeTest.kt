package io.github.jorgetroya80.madmobility.shared.emt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * SC15: real EMT login and one BiciMAD call through the production beans, checking the response
 * still matches the fixtures in src/test/resources/emt. Two EMT calls per run. Manual only:
 * `./gradlew smokeTest` with api/.env.local.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EmtSmokeTest(
    @Autowired private val auth: EmtAuth,
    @Autowired private val client: EmtHttpClient,
) {
    @Test
    fun `logs in and lists BiciMAD stations shaped like the fixtures`() {
        auth.accessToken()
        val renewsAt = requireNotNull(auth.tokenRenewsAt())
        assertThat(Duration.between(Instant.now(), renewsAt)).isBetween(Duration.ofHours(23), Duration.ofHours(24))

        val stations = client.get("bicimad", STATIONS, Map::class.java)

        assertThat(stations).hasSizeGreaterThan(600)
        val fixtureStation = JsonMapper().readTree(File(FIXTURE)).path("data").first()
        val fixtureKeys = fixtureStation.propertyNames().asSequence().toSet()
        assertThat(stations).allSatisfy { station -> assertThat(station.keys).containsExactlyInAnyOrderElementsOf(fixtureKeys) }
        assertThat(stations.map { it["light"] }).allSatisfy { assertThat(it).isIn(0, 1, 2, 3) }
        log.info("EMT smoke test OK: {} stations, token renews at {}", stations.size, renewsAt)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmtSmokeTest::class.java)
        const val STATIONS = "/v1/transport/bicimad/stations/"
        const val FIXTURE = "src/test/resources/emt/bicimad-stations.json"

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
