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

    @Test
    fun `with the quota spent, the cache serves the previous value as stale`() {
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
        val cache = CacheService(CacheProperties(), clock, SimpleMeterRegistry())
        val load = { client.get("bicimad", STATIONS, Map::class.java) }

        val fresh = cache.get("bicimad", "stations", load)
        clock.advance(java.time.Duration.ofMinutes(2))
        val stale = cache.get("bicimad", "stations", load)

        assertThat(stale.stale).isTrue()
        assertThat(stale.value).hasSize(3)
        assertThat(stale.updatedAt).isEqualTo(fresh.updatedAt)
        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo(STATIONS)))).hasSize(1)
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
    }
}
