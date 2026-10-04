package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/** With a module's quota spent, EmtHttpClient fails fast and the EMT receives nothing. */
class EmtQuotaIntegrationTest : EmtWireMockTest() {
    @Test
    fun `exhausted module quota stops requests before they reach the EMT`() {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS)).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(fixture("bicimad-stations.json")),
            ),
        )
        val clock = MutableClock()
        val quota =
            QuotaTracker(QuotaProperties(modules = mapOf("bicimad" to QuotaProperties.ModuleQuota(1))), clock, SimpleMeterRegistry())
        val restClient = restClient()
        val client =
            EmtHttpClient(
                restClient,
                EmtAuth(restClient, properties, clock, SimpleMeterRegistry(), quota),
                CircuitBreaker.ofDefaults("test"),
                NO_RETRY,
                quota,
            )

        client.get("bicimad", STATIONS, Map::class.java)

        assertThatThrownBy { client.get("bicimad", STATIONS, Map::class.java) }
            .isInstanceOfSatisfying(EmtQuotaExceeded::class.java) {
                assertThat(it.module).isEqualTo("bicimad")
                assertThat(it.resetsAt).isEqualTo(quota.resetsAt())
            }
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo(STATIONS)))).hasSize(1)
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
    }
}
