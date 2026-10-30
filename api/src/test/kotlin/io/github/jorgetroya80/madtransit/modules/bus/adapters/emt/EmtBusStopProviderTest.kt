package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.github.jorgetroya80.madtransit.shared.emt.CacheProperties
import io.github.jorgetroya80.madtransit.shared.emt.EmtProtocolError
import io.github.jorgetroya80.madtransit.shared.emt.EmtUnavailable
import io.github.jorgetroya80.madtransit.shared.emt.EmtWireMockTest
import io.github.jorgetroya80.madtransit.shared.emt.QuotaProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration
import java.time.Instant

// A fresh context per test: the cache would otherwise carry stops from one test to the next
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(EmtBusStopProviderTest.Config::class)
class EmtBusStopProviderTest(
    @Autowired private val provider: EmtBusStopProvider,
    @Autowired private val clock: MutableClock,
    @Autowired private val quota: QuotaProperties,
    @Autowired private val cache: CacheProperties,
) : EmtWireMockTest() {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock()
    }

    @BeforeEach
    fun login() = stubLogin()

    @Test
    fun `translates the EMT stops to the domain with public line labels`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))

        val snapshot = provider.snapshot()

        assertThat(snapshot.stops.map { it.number }).containsExactly("72", "2115", "2768", "4025", "1")
        assertThat(snapshot.stops.single { it.number == "2768" }.lines).containsExactly("73", "N3")
        assertThat(snapshot.updatedAt).isEqualTo(clock.now)
        assertThat(snapshot.stale).isFalse()
    }

    @Test
    fun `the stop list is a POST without a body`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))

        provider.snapshot()

        assertThat(wireMock.findAll(postRequestedFor(urlPathEqualTo(STOPS))).single().bodyAsString).isEmpty()
    }

    @Test
    fun `the line catalog is asked for today in Madrid`() {
        // 23:30 UTC on 30 October is already 31 October in Madrid (UTC+1)
        clock.now = Instant.parse("2026-10-30T23:30:00Z")
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))

        provider.snapshot()

        assertThat(wireMock.findAll(getRequestedFor(urlPathEqualTo("$LINES_PREFIX/20261031/")))).hasSize(1)
    }

    @Test
    fun `two snapshots within the TTL call each EMT endpoint once`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))

        provider.snapshot()
        clock.advance(Duration.ofHours(23))
        provider.snapshot()

        assertThat(stopCalls()).isEqualTo(1)
        assertThat(lineCalls()).isEqualTo(1)
    }

    @Test
    fun `snapshots of the same refresh share one stop index`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))

        val first = provider.snapshot()
        val second = provider.snapshot()

        assertThat(second.index).isSameAs(first.index)
        assertThat(second.find("2768")?.lines).containsExactly("73", "N3")
    }

    @Test
    fun `with the EMT down, the previous stops are served as stale`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))
        val fresh = provider.snapshot()
        stubStopsStatus(503)
        stubLinesStatus(503)
        clock.advance(Duration.ofHours(25))

        val stale = provider.snapshot()

        assertThat(stale.stale).isTrue()
        assertThat(stale.updatedAt).isEqualTo(fresh.updatedAt)
        assertThat(stale.stops).isEqualTo(fresh.stops)
    }

    @Test
    fun `a stale catalog makes the snapshot stale and as old as the catalog`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))
        val fresh = provider.snapshot()
        stubLinesStatus(503)
        clock.advance(Duration.ofHours(25))

        val snapshot = provider.snapshot()

        assertThat(stopCalls()).isEqualTo(2)
        assertThat(snapshot.stale).isTrue()
        assertThat(snapshot.updatedAt).isEqualTo(fresh.updatedAt)
    }

    @Test
    fun `stops older than max-stale are not served`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))
        provider.snapshot()
        stubStopsStatus(503)
        clock.advance(Duration.ofDays(8))

        assertThatThrownBy { provider.snapshot() }.isInstanceOf(EmtUnavailable::class.java)
    }

    @Test
    fun `no valid stop without a cached value is a protocol error`() {
        stubStops(EMPTY_RESPONSE)
        stubLines(fixture("bus-lines-info.json"))

        assertThatThrownBy { provider.snapshot() }.isInstanceOf(EmtProtocolError::class.java)
    }

    @Test
    fun `an empty catalog without a cached value is a protocol error`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(EMPTY_RESPONSE)

        assertThatThrownBy { provider.snapshot() }.isInstanceOf(EmtProtocolError::class.java)
    }

    @Test
    fun `an empty list or catalog after a good load serves the previous stops as stale`() {
        stubStops(fixture("bus-stops-list.json"))
        stubLines(fixture("bus-lines-info.json"))
        val fresh = provider.snapshot()
        stubStops(EMPTY_RESPONSE)
        stubLines(EMPTY_RESPONSE)
        clock.advance(Duration.ofHours(25))

        val stale = provider.snapshot()

        assertThat(stale.stale).isTrue()
        assertThat(stale.stops).isEqualTo(fresh.stops)
    }

    @Test
    fun `stop list and line catalog have their own daily quota and a daily cache kept for a week`() {
        listOf("bus-stops", "bus-lines").forEach { module ->
            assertThat(quota.modules[module]?.dailyLimit).isEqualTo(50)
            assertThat(cache.ttl(module)).isEqualTo(Duration.ofHours(24))
            assertThat(cache.maxStale(module)).isEqualTo(Duration.ofDays(7))
        }
    }

    private fun stubStops(body: String) = wireMock.stubFor(post(urlPathEqualTo(STOPS)).withJson(body))

    private fun stubLines(body: String) = wireMock.stubFor(get(urlPathMatching(LINES)).withJson(body))

    private fun stubStopsStatus(status: Int) = wireMock.stubFor(post(urlPathEqualTo(STOPS)).willReturn(aResponse().withStatus(status)))

    private fun stubLinesStatus(status: Int) = wireMock.stubFor(get(urlPathMatching(LINES)).willReturn(aResponse().withStatus(status)))

    private fun MappingBuilder.withJson(body: String) =
        willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body))

    private fun stopCalls() = wireMock.findAll(postRequestedFor(urlPathEqualTo(STOPS))).size

    private fun lineCalls() = wireMock.findAll(getRequestedFor(urlPathMatching(LINES))).size

    companion object {
        private const val STOPS = "/v1/transport/busemtmad/stops/list/"
        private const val LINES_PREFIX = "/v1/transport/busemtmad/lines/info"
        private const val LINES = "$LINES_PREFIX/[0-9]{8}/"
        private const val EMPTY_RESPONSE = """{"code":"00","data":[]}"""

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-transit.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
