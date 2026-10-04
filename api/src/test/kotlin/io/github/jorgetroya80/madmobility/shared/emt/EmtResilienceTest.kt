package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/** Retry and circuit breaker over real HTTP, with the spec's rules and shorter waits. */
class EmtResilienceTest : EmtWireMockTest() {
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

    private val circuitBreaker =
        CircuitBreaker.of(
            "test",
            CircuitBreakerConfig
                .custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofMillis(300))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(EmtUnavailable::class.java)
                .build(),
        )

    private fun client(
        props: EmtProperties = properties,
        retry: Retry = this.retry,
    ): EmtHttpClient {
        val restClient = restClient(props)
        return EmtHttpClient(
            restClient,
            EmtAuth(restClient, props, MutableClock(), SimpleMeterRegistry(), unlimitedQuota()),
            circuitBreaker,
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

    @Test
    fun `five failures out of ten open the circuit, which half-opens after the wait`() {
        stubLogin()
        val client = client(retry = NO_RETRY)
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(ok()))
        repeat(5) { client.get("bicimad", STATIONS, Map::class.java) }
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(aResponse().withStatus(503)))
        repeat(5) { runCatching { client.get("bicimad", STATIONS, Map::class.java) } }
        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)
        val callsWhenOpened = stationCalls()

        assertThatThrownBy { client.get("bicimad", STATIONS, Map::class.java) }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) { assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.CIRCUIT_OPEN) }
        assertThat(stationCalls()).isEqualTo(callsWhenOpened)

        Thread.sleep(400) // waitDurationInOpenState is 300 ms in this test
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(ok()))
        assertThat(client.get("bicimad", STATIONS, Map::class.java)).hasSize(3)
        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.HALF_OPEN)
    }

    @Test
    fun `protocol errors do not open the circuit`() {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS)).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody("""{"code":"42","data":[]}"""),
            ),
        )
        val client = client(retry = NO_RETRY)

        repeat(10) { runCatching { client.get("bicimad", STATIONS, Map::class.java) } }

        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
    }
}
