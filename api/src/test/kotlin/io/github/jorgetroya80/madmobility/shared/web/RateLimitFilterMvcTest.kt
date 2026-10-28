package io.github.jorgetroya80.madmobility.shared.web

import io.github.jorgetroya80.madmobility.shared.emt.EmtWireMockTest.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

/** SC8 end to end: the filter runs after RequestIdFilter and before MVC, with the application's clock and mapper. */
@SpringBootTest(properties = ["mad-mobility.rate-limit.capacity=60", "mad-mobility.rate-limit.refill-per-second=1"])
@AutoConfigureMockMvc
class RateLimitFilterMvcTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val clock: MutableClock,
) {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fixedClock(): MutableClock = MutableClock()
    }

    private fun ping(ip: String = CLIENT): MvcTestResult =
        mvc
            .get()
            .uri(PING)
            .header(RequestIdFilter.HEADER, "req-limited")
            .with { it.apply { remoteAddr = ip } }
            .exchange()

    // Behind Render every request comes from the proxy; Cloudflare puts the client's IP in True-Client-Ip
    private fun pingThroughProxy(
        clientIp: String?,
        forwardedFor: String? = null,
        proxy: String = PROXY,
    ): MvcTestResult =
        mvc
            .get()
            .uri(PING)
            .with {
                it.apply {
                    remoteAddr = proxy
                    clientIp?.let { ip -> addHeader(RateLimitFilter.CLIENT_IP_HEADER, ip) }
                    forwardedFor?.let { ip -> addHeader("X-Forwarded-For", ip) }
                }
            }.exchange()

    private fun exhaust(request: () -> MvcTestResult) {
        assertThat((1..60).map { request().response.status }).containsOnly(200)
        assertThat(request()).hasStatus(429)
    }

    @Test
    fun `the 61st request in the same instant is a 429 problem, other IPs and health are not limited`() {
        assertThat((1..60).map { ping().response.status }).containsOnly(200)

        val rejected = ping()

        assertThat(rejected)
            .hasStatus(429)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .hasHeader(HttpHeaders.RETRY_AFTER, "1")
            .hasHeader(RequestIdFilter.HEADER, "req-limited")
        val json = JsonMapper().readTree(rejected.response.contentAsString)
        assertThat(json.path("status").asInt()).isEqualTo(429)
        assertThat(json.path("instance").asString()).isEqualTo(PING)
        assertThat(json.path("requestId").asString()).isEqualTo("req-limited")
        assertThat(ping(OTHER_CLIENT)).hasStatusOk()
        assertThat(mvc.get().uri("/health/live").with { it.apply { remoteAddr = CLIENT } }).hasStatusOk()

        clock.advance(Duration.ofSeconds(1))

        assertThat(listOf(ping().response.status, ping().response.status)).containsExactly(200, 429)
    }

    @Test
    fun `behind the proxy each True-Client-Ip has its own bucket`() {
        exhaust { pingThroughProxy("203.0.113.1") }

        assertThat(pingThroughProxy("203.0.113.2")).hasStatusOk()
    }

    @Test
    fun `X-Forwarded-For does not give a client a new bucket`() {
        exhaust { pingThroughProxy("203.0.113.3") }

        assertThat(pingThroughProxy("203.0.113.3", forwardedFor = "192.0.2.99")).hasStatus(429)
    }

    @Test
    fun `without True-Client-Ip, or with a blank one, the bucket is the remote address`() {
        exhaust { pingThroughProxy(null, proxy = OTHER_PROXY) }

        assertThat(pingThroughProxy(" ", proxy = OTHER_PROXY)).hasStatus(429)
    }

    private companion object {
        const val PING = "/v1/test/ping"
        const val CLIENT = "198.51.100.7"
        const val OTHER_CLIENT = "198.51.100.8"
        const val PROXY = "10.0.0.1"
        const val OTHER_PROXY = "10.0.0.2"
    }
}
