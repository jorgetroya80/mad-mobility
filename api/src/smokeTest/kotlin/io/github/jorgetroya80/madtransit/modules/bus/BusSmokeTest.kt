package io.github.jorgetroya80.madtransit.modules.bus

import io.github.jorgetroya80.madtransit.shared.emt.EmtSmokeTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.GZIPInputStream

/**
 * The three bus endpoints through a real server against the real EMT, with assertions that hold
 * for any live data (arrivals may be empty at night). Also logs the real gzip saving of the full
 * stop list. Manual only: `./gradlew smokeTest` with api/.env.local.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BusSmokeTest(
    @LocalServerPort private val port: Int,
) {
    // HTTP/1.1: the JDK client would otherwise offer an h2c upgrade the server does not need
    private val http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    @Test
    fun `lists bus stops with lines near Sol`() {
        val stops = getJson("$STOPS?near=$SOL").path("stops")

        assertThat(stops).anySatisfy { stop -> assertThat(stop.path("lines").size()).isGreaterThan(0) }
        log.info("Bus smoke test: {} stops near Sol", stops.size())
    }

    @Test
    fun `shows a bus stop with its lines`() {
        val lines = getJson("$STOPS/$STOP").path("stop").path("lines")

        assertThat(lines.map { it.path("label").asString() }).contains(LINE)
        log.info("Bus smoke test: stop {} has {} lines", STOP, lines.size())
    }

    @Test
    fun `shows the next arrivals at a bus stop`() {
        val arrivals = getJson("$STOPS/$STOP/arrivals").path("arrivals")

        assertThat(arrivals.isArray).isTrue()
        assertThat(arrivals).allSatisfy { arrival ->
            assertThat(arrival.propertyNames()).contains("line", "destination", "noEstimate")
        }
        log.info("Bus smoke test: {} arrivals at stop {}", arrivals.size(), STOP)
    }

    // Logged, not asserted: the real size depends on the day's stop list
    @Test
    fun `logs the gzipped and raw size of the full stop list`() {
        val response = request(STOPS, HttpHeaders.ACCEPT_ENCODING to GZIP)

        assertThat(response.statusCode()).isEqualTo(200)
        val encoding = response.headers().firstValue(HttpHeaders.CONTENT_ENCODING).orElse("none")
        val raw = if (encoding == GZIP) gunzip(response.body()) else response.body()
        log.info("Bus smoke test: stop list is {} bytes sent ({}), {} bytes raw", response.body().size, encoding, raw.size)
    }

    private fun getJson(path: String): JsonNode {
        val response = request(path)
        assertThat(response.statusCode()).isEqualTo(200)
        return JSON.readTree(response.body())
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

    companion object {
        private val log = LoggerFactory.getLogger(BusSmokeTest::class.java)
        private val JSON = JsonMapper()
        private const val STOPS = "/v1/bus/stops"
        private const val SOL = "40.4168,-3.7038"
        private const val STOP = "2768"
        private const val LINE = "73"
        private const val GZIP = "gzip"

        @JvmStatic
        @BeforeAll
        fun requireCredentials() = EmtSmokeTest.requireCredentials()
    }
}
