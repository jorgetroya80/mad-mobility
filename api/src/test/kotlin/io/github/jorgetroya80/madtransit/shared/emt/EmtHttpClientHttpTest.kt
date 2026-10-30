package io.github.jorgetroya80.madtransit.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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

/** Real HTTP against WireMock with the real EmtAuth: timeouts and the relogin flow end to end. */
class EmtHttpClientHttpTest : EmtWireMockTest() {
    private fun client(
        props: EmtProperties = properties,
        retry: Retry = NO_RETRY,
        quota: QuotaTracker = unlimitedQuota(),
    ): EmtHttpClient {
        // Login always uses production timeouts so a slow cold login can't make timeout tests pass or flake
        val authClient = restClient(properties)
        return EmtHttpClient(
            restClient(props),
            EmtAuth(authClient, properties, MutableClock(), SimpleMeterRegistry(), unlimitedQuota()),
            retry,
            quota,
        )
    }

    private fun loginCalls() = wireMock.findAll(getRequestedFor(urlPathEqualTo(LOGIN_PATH))).size

    private fun arrivalsCalls() = wireMock.findAll(postRequestedFor(urlPathEqualTo(ARRIVALS))).size

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

    @Test
    fun `dropped connection throws EmtUnavailable with CONNECTION_FAILED`() {
        stubLogin()
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        assertThatThrownBy { client().get("bicimad", STATIONS, Map::class.java) }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.CONNECTION_FAILED)
            }
    }

    @Test
    fun `post sends the JSON body and the access token and returns the data`() {
        stubLogin()
        wireMock.stubFor(post(urlPathEqualTo(ARRIVALS)).willReturn(stubStations(200, "bus-arrivals.json")))

        val stops = client().post("bus-arrivals", ARRIVALS, Map::class.java, ARRIVALS_BODY)

        assertThat(stops).hasSize(1)
        assertThat(stops.first()["Arrive"] as List<*>).hasSize(6)
        wireMock.verify(
            postRequestedFor(urlPathEqualTo(ARRIVALS))
                .withHeader("accessToken", equalTo(FIXTURE_TOKEN))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("""{"cultureInfo":"EN","Text_EstimationsRequired_YN":"Y"}""")),
        )
    }

    @Test
    fun `post without a body sends an empty POST`() {
        stubLogin()
        wireMock.stubFor(post(urlPathEqualTo(STOPS_LIST)).willReturn(stubStations(200, "bus-stops-list.json")))

        val stops = client().post("bus-stops", STOPS_LIST, Map::class.java)

        assertThat(stops).hasSize(5)
        wireMock.verify(
            postRequestedFor(urlPathEqualTo(STOPS_LIST))
                .withHeader("Content-Type", absent())
                .withRequestBody(absent()),
        )
    }

    @Test
    fun `post counts against the quota and retries a dropped connection once`() {
        stubLogin()
        wireMock.stubFor(
            post(urlPathEqualTo(ARRIVALS))
                .inScenario("dropped")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                .willSetStateTo("recovered"),
        )
        wireMock.stubFor(
            post(urlPathEqualTo(ARRIVALS))
                .inScenario("dropped")
                .whenScenarioStateIs("recovered")
                .willReturn(stubStations(200, "bus-arrivals.json")),
        )
        val quota = unlimitedQuota()
        val oneRetry =
            Retry.of(
                "one-retry",
                RetryConfig
                    .custom<Any>()
                    .maxAttempts(2)
                    .retryExceptions(EmtUnavailable::class.java)
                    .build(),
            )

        assertThat(client(retry = oneRetry, quota = quota).post("bus-arrivals", ARRIVALS, Map::class.java, ARRIVALS_BODY)).hasSize(1)
        assertThat(arrivalsCalls()).isEqualTo(2)
        assertThat(quota.used("bus-arrivals")).isEqualTo(2)
    }

    @Test
    fun `relogs in on HTTP 200 code 80 and retries once`() {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(LINES))
                .inScenario("token")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(stubStations(200, "bus-token-invalid.json"))
                .willSetStateTo("relogged"),
        )
        wireMock.stubFor(
            get(urlPathEqualTo(LINES))
                .inScenario("token")
                .whenScenarioStateIs("relogged")
                .willReturn(stubStations(200, "bus-lines-info.json")),
        )

        assertThat(client().get("bus-lines", LINES, Map::class.java)).isNotEmpty()
        assertThat(loginCalls()).isEqualTo(2)
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(LINES)))
    }

    @Test
    fun `code 80 on HTTP 200 with a fresh token too throws EmtProtocolError after one relogin`() {
        stubLogin()
        wireMock.stubFor(post(urlPathEqualTo(ARRIVALS)).willReturn(stubStations(200, "bus-arrivals-missing-stop.json")))

        assertThatThrownBy { client().post("bus-arrivals", ARRIVALS, Map::class.java, ARRIVALS_BODY) }
            .isInstanceOfSatisfying(EmtProtocolError::class.java) { assertThat(it.code).isEqualTo("80") }
        assertThat(loginCalls()).isEqualTo(2)
        assertThat(arrivalsCalls()).isEqualTo(2)
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
        const val STOPS_LIST = "/v1/transport/busemtmad/stops/list/"
        const val LINES = "/v1/transport/busemtmad/lines/info/20261030/"
        const val ARRIVALS = "/v2/transport/busemtmad/stops/72/arrives/"
        val ARRIVALS_BODY = mapOf("cultureInfo" to "EN", "Text_EstimationsRequired_YN" to "Y")
    }
}
