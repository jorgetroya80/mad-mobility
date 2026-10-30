package io.github.jorgetroya80.madtransit.modules.bus.http

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.github.jorgetroya80.madtransit.shared.emt.EmtWireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** `GET /v1/bus/stops/{number}/arrivals` from the HTTP request down to a fake EMT. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(BusStopArrivalsEndpointTest.Config::class)
class BusStopArrivalsEndpointTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val clock: MutableClock,
) : EmtWireMockTest() {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testClock() = MutableClock()
    }

    @BeforeEach
    fun stubEmt() {
        stubLogin()
        wireMock.stubFor(post(urlPathEqualTo(STOPS)).withJson(fixture("bus-stops-list.json")))
        wireMock.stubFor(get(urlPathMatching(LINES)).withJson(fixture("bus-lines-info.json")))
    }

    @Test
    fun `SC3 stop 72 gives its arrivals from the EMT, soonest first`() {
        stubArrivals(fixture("bus-arrivals.json"))

        val result = mvc.get().uri(ARRIVALS_72).exchange()

        assertThat(result).hasStatusOk()
        val arrivals = JsonMapper().readTree(result.response.contentAsString).path("arrivals").values()
        assertThat(arrivals.map { it.path("line").asString() }).containsExactly("14", "45", "37", "53", "5", "C03")
    }

    @Test
    fun `a stop number not in the list is a 404 without asking the EMT for arrivals`() {
        val result = mvc.get().uri("/v1/bus/stops/99999999/arrivals").exchange()

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        wireMock.verify(0, postRequestedFor(urlPathMatching(ANY_ARRIVES)))
    }

    @Test
    fun `SC4 a listed stop the EMT answers with code 80 even after logging in again is the standard 502`() {
        stubArrivals(fixture("bus-arrivals-missing-stop.json"))

        val result = mvc.get().uri(ARRIVALS_72).exchange()

        assertThat(result).hasStatus(502).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        val problem = JsonMapper().readTree(result.response.contentAsString)
        assertThat(problem.path("type").asString()).isEqualTo("urn:mad-transit:problem:emt-bad-response")
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(ARRIVES_72)))
        // The first login and exactly one more after the EMT rejected the token
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(LOGIN_PATH)))
    }

    @Test
    fun `a stop the EMT does not serve is remembered for 30 s without asking the EMT or logging in again`() {
        stubArrivals(fixture("bus-arrivals-missing-stop.json"))

        val first = mvc.get().uri(ARRIVALS_72).exchange()
        clock.advance(Duration.ofSeconds(29))
        val second = mvc.get().uri(ARRIVALS_72).exchange()

        listOf(first, second).forEach { result ->
            assertThat(result).hasStatus(502).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            val problem = JsonMapper().readTree(result.response.contentAsString)
            assertThat(problem.path("type").asString()).isEqualTo("urn:mad-transit:problem:emt-bad-response")
        }
        // Only the first request: its attempt and its single relogin
        wireMock.verify(2, postRequestedFor(urlPathEqualTo(ARRIVES_72)))
        wireMock.verify(2, getRequestedFor(urlPathEqualTo(LOGIN_PATH)))

        clock.advance(Duration.ofSeconds(1))
        assertThat(mvc.get().uri(ARRIVALS_72).exchange()).hasStatus(502)
        wireMock.verify(4, postRequestedFor(urlPathEqualTo(ARRIVES_72)))
        wireMock.verify(3, getRequestedFor(urlPathEqualTo(LOGIN_PATH)))
    }

    @Test
    fun `SC5 with the EMT down and arrivals older than 2 minutes, the API answers 503`() {
        stubArrivals(fixture("bus-arrivals.json"))
        assertThat(mvc.get().uri(ARRIVALS_72).exchange()).hasStatusOk()
        wireMock.stubFor(post(urlPathEqualTo(ARRIVES_72)).willReturn(aResponse().withStatus(503)))

        clock.advance(Duration.ofMinutes(2))
        val stale = mvc.get().uri(ARRIVALS_72).exchange()
        clock.advance(Duration.ofSeconds(1))
        val expired = mvc.get().uri(ARRIVALS_72).exchange()

        assertThat(stale).hasStatusOk()
        assertThat(JsonMapper().readTree(stale.response.contentAsString).path("stale").asBoolean()).isTrue()
        assertThat(expired).hasStatus(503).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
    }

    private fun stubArrivals(body: String) = wireMock.stubFor(post(urlPathEqualTo(ARRIVES_72)).withJson(body))

    private fun MappingBuilder.withJson(body: String) =
        willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body))

    companion object {
        private const val STOPS = "/v1/transport/busemtmad/stops/list/"
        private const val LINES = "/v1/transport/busemtmad/lines/info/[0-9]{8}/"
        private const val ARRIVES_72 = "/v2/transport/busemtmad/stops/72/arrives/"
        private const val ANY_ARRIVES = "/v2/transport/busemtmad/stops/.*/arrives/"
        private const val ARRIVALS_72 = "/v1/bus/stops/72/arrivals"

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-transit.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
