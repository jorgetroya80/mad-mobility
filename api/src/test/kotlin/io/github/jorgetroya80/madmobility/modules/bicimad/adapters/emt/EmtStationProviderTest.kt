package io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import io.github.jorgetroya80.madmobility.shared.emt.EmtProtocolError
import io.github.jorgetroya80.madmobility.shared.emt.EmtWireMockTest
import io.github.jorgetroya80.madmobility.shared.emt.QuotaProperties
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

// A fresh context per test: the cache would otherwise carry stations from one test to the next
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(EmtStationProviderTest.Config::class)
class EmtStationProviderTest(
    @Autowired private val provider: EmtStationProvider,
    @Autowired private val clock: MutableClock,
    @Autowired private val quota: QuotaProperties,
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
    fun `translates the EMT stations to the domain`() {
        stubStations(fixture("bicimad-stations.json"))

        val snapshot = provider.snapshot()

        assertThat(snapshot.stations.map { it.id }).containsExactly(1409, 1493, 2397)
        assertThat(snapshot.stations.first().name).isEqualTo("Fuencarral")
        assertThat(snapshot.updatedAt).isEqualTo(clock.now)
        assertThat(snapshot.stale).isFalse()
    }

    @Test
    fun `two snapshots within the TTL call the EMT once`() {
        stubStations(fixture("bicimad-stations.json"))

        provider.snapshot()
        clock.advance(Duration.ofSeconds(59))
        provider.snapshot()

        assertThat(stationCalls()).isEqualTo(1)
    }

    @Test
    fun `with the EMT down, the previous stations are served as stale`() {
        stubStations(fixture("bicimad-stations.json"))
        val fresh = provider.snapshot()
        wireMock.stubFor(get(urlPathEqualTo(STATIONS)).willReturn(aResponse().withStatus(503)))
        clock.advance(Duration.ofMinutes(2))

        val stale = provider.snapshot()

        assertThat(stale.stale).isTrue()
        assertThat(stale.updatedAt).isEqualTo(fresh.updatedAt)
        assertThat(stale.stations).isEqualTo(fresh.stations)
    }

    @Test
    fun `no valid station without a cached value is a protocol error`() {
        stubStations("""{"code":"00","data":[{"id":1,"virtualDelete":true}]}""")

        assertThatThrownBy { provider.snapshot() }.isInstanceOf(EmtProtocolError::class.java)
    }

    @Test
    fun `bicimad has its own daily quota`() {
        assertThat(quota.modules["bicimad"]?.dailyLimit).isEqualTo(3000)
    }

    private fun stubStations(body: String) {
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS)).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)),
        )
    }

    private fun stationCalls() = wireMock.findAll(getRequestedFor(urlPathEqualTo(STATIONS))).size

    companion object {
        private const val STATIONS = "/v1/transport/bicimad/stations/"

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-mobility.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
