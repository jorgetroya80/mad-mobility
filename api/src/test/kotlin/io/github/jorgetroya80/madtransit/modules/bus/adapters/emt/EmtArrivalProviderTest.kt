package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.github.jorgetroya80.madtransit.shared.emt.CacheProperties
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

// A fresh context per test: the cache would otherwise carry arrivals from one test to the next
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(EmtArrivalProviderTest.Config::class)
class EmtArrivalProviderTest(
    @Autowired private val provider: EmtArrivalProvider,
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
    fun `translates the EMT arrivals of the stop to the domain`() {
        stubArrivals("72", fixture("bus-arrivals.json"))

        val snapshot = provider.arrivals("72")

        assertThat(snapshot.arrivals.map { it.line }).containsExactly("14", "45", "37", "53", "5", "C03")
        assertThat(snapshot.updatedAt).isEqualTo(clock.now)
        assertThat(snapshot.stale).isFalse()
    }

    @Test
    fun `asks the EMT for estimates only, in Spanish, with the access token`() {
        stubArrivals("72", fixture("bus-arrivals.json"))

        provider.arrivals("72")

        wireMock.verify(
            1,
            postRequestedFor(urlPathEqualTo(arrivesPath("72")))
                .withHeader("accessToken", equalTo(FIXTURE_TOKEN))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(ARRIVES_REQUEST)),
        )
    }

    @Test
    fun `no bus coming is an empty list, not an error`() {
        stubArrivals("72", """{"code":"00","data":[{"Arrive":[],"StopInfo":[],"ExtraInfo":[],"Incident":{}}]}""")
        stubArrivals("2768", """{"code":"00","data":[]}""")

        assertThat(provider.arrivals("72").arrivals).isEmpty()
        assertThat(provider.arrivals("2768").arrivals).isEmpty()
    }

    @Test
    fun `a second request within the 30 s TTL does not call the EMT`() {
        stubArrivals("72", fixture("bus-arrivals.json"))

        provider.arrivals("72")
        clock.advance(Duration.ofSeconds(29))
        provider.arrivals("72")

        assertThat(arrivesCalls("72")).isEqualTo(1)
    }

    @Test
    fun `arrivals older than the TTL are asked for again, per stop`() {
        stubArrivals("72", fixture("bus-arrivals.json"))
        stubArrivals("2768", fixture("bus-arrivals.json"))

        provider.arrivals("72")
        provider.arrivals("2768")
        clock.advance(Duration.ofSeconds(30))
        provider.arrivals("72")

        assertThat(arrivesCalls("72")).isEqualTo(2)
        assertThat(arrivesCalls("2768")).isEqualTo(1)
    }

    @Test
    fun `SC5 with the EMT down, arrivals up to 2 minutes old are served as stale`() {
        stubArrivals("72", fixture("bus-arrivals.json"))
        val fresh = provider.arrivals("72")
        stubArrivesStatus("72", 503)
        clock.advance(Duration.ofMinutes(2))

        val stale = provider.arrivals("72")

        assertThat(stale.stale).isTrue()
        assertThat(stale.updatedAt).isEqualTo(fresh.updatedAt)
        assertThat(stale.arrivals).isEqualTo(fresh.arrivals)
    }

    @Test
    fun `SC5 arrivals older than 2 minutes are not served`() {
        stubArrivals("72", fixture("bus-arrivals.json"))
        provider.arrivals("72")
        stubArrivesStatus("72", 503)
        clock.advance(Duration.ofMinutes(2).plusSeconds(1))

        assertThatThrownBy { provider.arrivals("72") }.isInstanceOf(EmtUnavailable::class.java)
    }

    @Test
    fun `arrivals have their own daily quota and a 30 s cache served stale for 2 minutes`() {
        assertThat(quota.modules["bus-arrivals"]?.dailyLimit).isEqualTo(10000)
        assertThat(cache.ttl("bus-arrivals")).isEqualTo(Duration.ofSeconds(30))
        assertThat(cache.maxStale("bus-arrivals")).isEqualTo(Duration.ofMinutes(2))
    }

    private fun stubArrivals(
        number: String,
        body: String,
    ) = wireMock.stubFor(post(urlPathEqualTo(arrivesPath(number))).withJson(body))

    private fun stubArrivesStatus(
        number: String,
        status: Int,
    ) = wireMock.stubFor(post(urlPathEqualTo(arrivesPath(number))).willReturn(aResponse().withStatus(status)))

    private fun MappingBuilder.withJson(body: String) =
        willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body))

    private fun arrivesCalls(number: String) = wireMock.findAll(postRequestedFor(urlPathEqualTo(arrivesPath(number)))).size

    private fun arrivesPath(number: String) = "/v2/transport/busemtmad/stops/$number/arrives/"

    companion object {
        private const val ARRIVES_REQUEST =
            """{"cultureInfo":"ES","Text_StopRequired_YN":"N","Text_EstimationsRequired_YN":"Y","Text_IncidencesRequired_YN":"N"}"""

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-transit.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
