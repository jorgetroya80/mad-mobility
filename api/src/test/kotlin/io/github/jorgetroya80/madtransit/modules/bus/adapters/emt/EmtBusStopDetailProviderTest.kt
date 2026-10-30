package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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

// A fresh context per test: the cache would otherwise carry details from one test to the next
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(EmtBusStopDetailProviderTest.Config::class)
class EmtBusStopDetailProviderTest(
    @Autowired private val provider: EmtBusStopDetailProvider,
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
    fun `translates the EMT detail of the stop to the domain`() {
        stubDetail("2768", fixture("bus-stop-detail.json"))

        val snapshot = provider.detail("2768")

        assertThat(snapshot.detail.address).isEqualTo("Ctra. de Canillas con C/ Emigrantes")
        assertThat(snapshot.detail.lines.map { it.label }).containsExactly("73", "N3")
        assertThat(snapshot.updatedAt).isEqualTo(clock.now)
        assertThat(snapshot.stale).isFalse()
    }

    @Test
    fun `today's day type is taken in Madrid`() {
        // 23:30 UTC on Friday 30 October is already Saturday 31 October in Madrid (UTC+1)
        clock.now = Instant.parse("2026-10-30T23:30:00Z")
        stubDetail("72", SEVERAL_DAY_TYPES)

        val line =
            provider
                .detail("72")
                .detail.lines
                .single()

        assertThat(line.firstBus).isEqualTo("07:00")
    }

    @Test
    fun `two details within the TTL call the EMT once`() {
        stubDetail("72", fixture("bus-stop-detail-72.json"))

        provider.detail("72")
        clock.advance(Duration.ofHours(23))
        provider.detail("72")

        assertThat(detailCalls("72")).isEqualTo(1)
    }

    @Test
    fun `each stop has its own cache entry`() {
        stubDetail("72", fixture("bus-stop-detail-72.json"))
        stubDetail("2768", fixture("bus-stop-detail.json"))

        provider.detail("72")
        provider.detail("2768")

        assertThat(detailCalls("72")).isEqualTo(1)
        assertThat(detailCalls("2768")).isEqualTo(1)
    }

    @Test
    fun `with the EMT down after a good load, the previous detail is served as stale`() {
        stubDetail("72", fixture("bus-stop-detail-72.json"))
        val fresh = provider.detail("72")
        stubDetailStatus("72", 503)
        clock.advance(Duration.ofHours(25))

        val stale = provider.detail("72")

        assertThat(stale.stale).isTrue()
        assertThat(stale.updatedAt).isEqualTo(fresh.updatedAt)
        assertThat(stale.detail).isEqualTo(fresh.detail)
    }

    @Test
    fun `a detail older than max-stale is not served`() {
        stubDetail("72", fixture("bus-stop-detail-72.json"))
        provider.detail("72")
        stubDetailStatus("72", 503)
        clock.advance(Duration.ofDays(8))

        assertThatThrownBy { provider.detail("72") }.isInstanceOf(EmtUnavailable::class.java)
    }

    @Test
    fun `a response without the stop is a protocol error`() {
        stubDetail("72", """{"code":"00","data":[]}""")
        stubDetail("2768", """{"code":"00","data":[{"stops":[]}]}""")

        assertThatThrownBy { provider.detail("72") }.isInstanceOf(EmtProtocolError::class.java)
        assertThatThrownBy { provider.detail("2768") }.isInstanceOf(EmtProtocolError::class.java)
    }

    @Test
    fun `the stop detail has its own daily quota and a daily cache kept for a week`() {
        assertThat(quota.modules["bus-stop-detail"]?.dailyLimit).isEqualTo(2000)
        assertThat(cache.ttl("bus-stop-detail")).isEqualTo(Duration.ofHours(24))
        assertThat(cache.maxStale("bus-stop-detail")).isEqualTo(Duration.ofDays(7))
    }

    private fun stubDetail(
        number: String,
        body: String,
    ) = wireMock.stubFor(get(urlPathEqualTo(detailPath(number))).withJson(body))

    private fun stubDetailStatus(
        number: String,
        status: Int,
    ) = wireMock.stubFor(get(urlPathEqualTo(detailPath(number))).willReturn(aResponse().withStatus(status)))

    private fun MappingBuilder.withJson(body: String) =
        willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body))

    private fun detailCalls(number: String) = wireMock.findAll(getRequestedFor(urlPathEqualTo(detailPath(number)))).size

    private fun detailPath(number: String) = "/v1/transport/busemtmad/stops/$number/detail/"

    companion object {
        private val SEVERAL_DAY_TYPES =
            """
            {"code":"00","data":[{"stops":[{"stop":"72","postalAddress":"Pº de Recoletos, 2","dataLine":[
              {"line":"005","label":"5","direction":"B","headerA":"SOL/SEVILLA","headerB":"CHAMARTIN",
               "startTime":"06:30","stopTime":"22:58","minFreq":"10","maxFreq":"22","dayType":"VI"},
              {"line":"005","label":"5","direction":"B","headerA":"SOL/SEVILLA","headerB":"CHAMARTIN",
               "startTime":"07:00","stopTime":"23:00","minFreq":"15","maxFreq":"25","dayType":"SA"}
            ]}]}]}
            """.trimIndent()

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-transit.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
