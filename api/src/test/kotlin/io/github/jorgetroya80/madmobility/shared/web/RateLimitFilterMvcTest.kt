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

    private companion object {
        const val PING = "/v1/test/ping"
        const val CLIENT = "198.51.100.7"
        const val OTHER_CLIENT = "198.51.100.8"
    }
}
