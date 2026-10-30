package io.github.jorgetroya80.madtransit.modules.bus.http

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.github.jorgetroya80.madtransit.shared.emt.EmtWireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.GZIPInputStream

/** gzip of the API responses, done by the embedded server: needs a real one, MockMvc never compresses. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusStopsCompressionTest(
    @LocalServerPort private val port: Int,
) : EmtWireMockTest() {
    // HTTP/1.1: the JDK client would otherwise offer an h2c upgrade the server does not need
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    @BeforeEach
    fun stubEmt() {
        stubLogin()
        wireMock.stubFor(post(urlPathEqualTo(STOPS)).withJson(manyStops()))
        wireMock.stubFor(get(urlPathMatching(LINES)).withJson(fixture("bus-lines-info.json")))
    }

    @Test
    fun `the stop list is gzipped when the client accepts it`() {
        val response = request(ALL_STOPS, HttpHeaders.ACCEPT_ENCODING to GZIP)

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).hasValue(GZIP)
        val stops = JSON.readTree(gunzip(response.body())).path("stops")
        assertThat(stops.size()).isEqualTo(STOP_COUNT)
    }

    @Test
    fun `the stop list is sent as is when the client does not accept gzip`() {
        val response = request(ALL_STOPS)

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).isEmpty
        assertThat(JSON.readTree(response.body()).path("stops").size()).isEqualTo(STOP_COUNT)
    }

    @Test
    fun `a response under 2 KB is not compressed`() {
        val response = request("$ALL_STOPS?radius=10", HttpHeaders.ACCEPT_ENCODING to GZIP)

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(response.body().size).isLessThan(MIN_COMPRESSED_BYTES)
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).isEmpty
    }

    @Test
    fun `a gzipped stop list still revalidates with its ETag`() {
        val etag =
            request(ALL_STOPS, HttpHeaders.ACCEPT_ENCODING to GZIP)
                .headers()
                .firstValue(HttpHeaders.ETAG)
                .orElseThrow()

        val response = request(ALL_STOPS, HttpHeaders.ACCEPT_ENCODING to GZIP, HttpHeaders.IF_NONE_MATCH to etag)

        assertThat(response.statusCode()).isEqualTo(304)
        assertThat(response.body()).isEmpty()
    }

    private fun request(
        path: String,
        vararg headers: Pair<String, String>,
    ): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
        headers.forEach { (name, value) -> request.header(name, value) }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun gunzip(body: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }

    // The fixture has 5 stops, under 2 KB once translated: repeat them with new numbers, like the real list
    private fun manyStops(): String {
        val list = JSON.readTree(fixture("bus-stops-list.json")) as ObjectNode
        val stops = list.path("data") as ArrayNode
        val copies =
            (0 until STOP_COUNT).map { i ->
                (stops[i % stops.size()] as ObjectNode).deepCopy().put("node", "${FIRST_NUMBER + i}")
            }
        stops.removeAll().addAll(copies)
        return JSON.writeValueAsString(list)
    }

    private fun MappingBuilder.withJson(body: String) =
        willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body))

    companion object {
        private const val ALL_STOPS = "/v1/bus/stops"
        private const val STOPS = "/v1/transport/busemtmad/stops/list/"
        private const val LINES = "/v1/transport/busemtmad/lines/info/[0-9]{8}/"
        private const val GZIP = "gzip"
        private const val STOP_COUNT = 200
        private const val FIRST_NUMBER = 10_000
        private const val MIN_COMPRESSED_BYTES = 2048
        private val JSON = JsonMapper()

        @JvmStatic
        @DynamicPropertySource
        fun emtBaseUrl(registry: DynamicPropertyRegistry) {
            registry.add("mad-transit.emt.base-url") { wireMock.baseUrl() }
        }
    }
}
