package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/** Real HTTP against WireMock with the real EmtAuth: timeouts and the relogin flow end to end. */
class EmtHttpClientHttpTest : EmtWireMockTest() {
    private fun client(props: EmtProperties = properties): EmtHttpClient {
        // Login always uses production timeouts so a slow cold login can't make timeout tests pass or flake
        val authClient = restClient(properties)
        return EmtHttpClient(
            restClient(props),
            EmtAuth(authClient, properties, MutableClock(), SimpleMeterRegistry(), unlimitedQuota()),
            CircuitBreaker.ofDefaults("test"),
            NO_RETRY,
            unlimitedQuota(),
        )
    }

    private fun stubStations(
        status: Int,
        fixture: String,
        delay: Duration = Duration.ZERO,
    ) = aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(fixture(fixture))
        .withFixedDelay(delay.toMillis().toInt())

    @Test
    fun `relogs in on HTTP 401 code 80 and retries once`() {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS))
                .inScenario("token")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(stubStations(401, "token-invalid.json"))
                .willSetStateTo("relogged"),
        )
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS))
                .inScenario("token")
                .whenScenarioStateIs("relogged")
                .willReturn(stubStations(200, "bicimad-stations.json")),
        )

        val stations = client().get("bicimad", STATIONS, Map::class.java)

        assertThat(stations).hasSize(3)
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo(LOGIN_PATH)))).hasSize(2)
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(STATIONS)).withHeader("accessToken", equalTo(FIXTURE_TOKEN)))
    }

    @Test
    fun `slow answer throws EmtUnavailable with TIMEOUT`() {
        stubLogin()
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(stubStations(200, "bicimad-stations.json", Duration.ofSeconds(1))))
        val props = EmtProperties(email = TEST_EMAIL, password = TEST_PASSWORD, readTimeout = Duration.ofMillis(200))

        assertThatThrownBy { client(props).get("bicimad", STATIONS, Map::class.java) }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.TIMEOUT)
            }
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(STATIONS)))
    }

    @Test
    fun `server error throws EmtUnavailable with SERVER_ERROR`() {
        stubLogin()
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(aResponse().withStatus(500)))

        assertThatThrownBy { client().get("bicimad", STATIONS, Map::class.java) }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.SERVER_ERROR)
            }
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
    }
}
