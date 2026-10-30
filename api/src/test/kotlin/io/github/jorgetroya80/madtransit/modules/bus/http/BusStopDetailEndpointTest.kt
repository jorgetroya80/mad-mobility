package io.github.jorgetroya80.madtransit.modules.bus.http

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.github.jorgetroya80.madtransit.shared.emt.EmtWireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper

/** `GET /v1/bus/stops/{number}` from the HTTP request down to a fake EMT. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusStopDetailEndpointTest(
    @Autowired private val mvc: MockMvcTester,
) : EmtWireMockTest() {
    @BeforeEach
    fun stubEmt() {
        stubLogin()
        wireMock.stubFor(post(urlPathEqualTo(STOPS)).withJson(fixture("bus-stops-list.json")))
        wireMock.stubFor(get(urlPathMatching(LINES)).withJson(fixture("bus-lines-info.json")))
        wireMock.stubFor(get(urlPathEqualTo(detailPath("2768"))).withJson(fixture("bus-stop-detail.json")))
    }

    @Test
    fun `a stop number not in the list is a 404 without asking the EMT for its detail`() {
        val result = mvc.get().uri("/v1/bus/stops/99999999").exchange()

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        wireMock.verify(0, getRequestedFor(urlPathMatching(ANY_DETAIL)))
    }

    @Test
    fun `stop 2768 combines the list entry with the EMT detail`() {
        val result = mvc.get().uri("/v1/bus/stops/2768").exchange()

        assertThat(result).hasStatusOk()
        val stop = JsonMapper().readTree(result.response.contentAsString).path("stop")
        assertThat(stop.path("name").asString()).isEqualTo("Emigrantes-Carretera de Canillas")
        assertThat(stop.path("address").asString()).isEqualTo("Ctra. de Canillas con C/ Emigrantes")
        assertThat(stop.path("lines").values().map { it.path("label").asString() }).containsExactly("73", "N3")
        wireMock.verify(1, getRequestedFor(urlPathEqualTo(detailPath("2768"))))
    }

    private fun MappingBuilder.withJson(body: String) =
        willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body))

    private fun detailPath(number: String) = "/v1/transport/busemtmad/stops/$number/detail/"

    companion object {
        private const val STOPS = "/v1/transport/busemtmad/stops/list/"
        private const val LINES = "/v1/transport/busemtmad/lines/info/[0-9]{8}/"
        private const val ANY_DETAIL = "/v1/transport/busemtmad/stops/.*/detail/"

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-transit.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
