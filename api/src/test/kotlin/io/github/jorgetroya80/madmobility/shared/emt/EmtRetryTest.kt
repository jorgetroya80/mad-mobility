package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/** Retry over real HTTP, with the spec's rules and shorter waits. */
class EmtRetryTest : EmtWireMockTest() {
    private val fastTimeouts = EmtProperties(email = TEST_EMAIL, password = TEST_PASSWORD, readTimeout = Duration.ofMillis(200))

    private val retry =
        Retry.of(
            "test",
            RetryConfig
                .custom<Any>()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(50))
                .retryExceptions(EmtUnavailable::class.java)
                .build(),
        )

    private fun client(
        props: EmtProperties = properties,
        retry: Retry = this.retry,
    ): EmtHttpClient {
        // Login always uses production timeouts so a slow cold login can't make timeout tests pass or flake
        val authClient = restClient(properties)
        return EmtHttpClient(
            restClient(props),
            EmtAuth(authClient, properties, MutableClock(), SimpleMeterRegistry(), unlimitedQuota()),
            retry,
            unlimitedQuota(),
        )
    }

    private fun stationCalls() = wireMock.findAll(getRequestedFor(urlPathEqualTo(STATIONS))).size

    private fun ok() =
        aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fixture("bicimad-stations.json"))

    @Test
    fun `a 500 followed by a 200 succeeds after one retry`() {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"),
        )
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).inScenario("flaky").whenScenarioStateIs("recovered").willReturn(ok()))

        assertThat(client().get("bicimad", STATIONS, Map::class.java)).hasSize(3)
        assertThat(stationCalls()).isEqualTo(2)
    }

    @Test
    fun `a dropped connection followed by a 200 succeeds after one retry`() {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS))
                .inScenario("dropped")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("recovered"),
        )
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).inScenario("dropped").whenScenarioStateIs("recovered").willReturn(ok()))

        assertThat(client().get("bicimad", STATIONS, Map::class.java)).hasSize(3)
        assertThat(stationCalls()).isEqualTo(2)
    }

    @Test
    fun `two timeouts throw EmtUnavailable within both timeouts plus the retry wait`() {
        stubLogin()
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(ok().withFixedDelay(1000)))
        val started = System.nanoTime()

        assertThatThrownBy { client(fastTimeouts).get("bicimad", STATIONS, Map::class.java) }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) { assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.TIMEOUT) }
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2))
        assertThat(stationCalls()).isEqualTo(2)
    }

    @Test
    fun `client errors are not retried`() {
        stubLogin()
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(aResponse().withStatus(404).withBody("<html>404</html>")))

        assertThatThrownBy { client().get("bicimad", STATIONS, Map::class.java) }.isInstanceOf(EmtProtocolError::class.java)
        assertThat(stationCalls()).isEqualTo(1)
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
    }
}
