package io.github.jorgetroya80.madmobility.shared.web

import io.github.jorgetroya80.madmobility.shared.emt.EmtWireMockTest.MutableClock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.ProblemDetailJacksonMixin
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RateLimitFilterTest {
    private val clock = MutableClock()
    private val meterRegistry = SimpleMeterRegistry()
    private val passed = AtomicInteger()
    private val chain = FilterChain { _, _ -> passed.incrementAndGet() }

    private fun filter(
        capacity: Int = 60,
        refillPerSecond: Int = 1,
    ) = RateLimitFilter(RateLimitProperties(capacity, refillPerSecond), clock, meterRegistry, PROBLEM_MAPPER)

    private fun RateLimitFilter.call(
        ip: String = CLIENT,
        path: String = PING,
    ): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", path).apply { remoteAddr = ip }
        return MockHttpServletResponse().also { doFilter(request, it, chain) }
    }

    @Test
    fun `allows the capacity and rejects the next request with a problem and Retry-After`() {
        val filter = filter()

        assertThat((1..60).map { filter.call().status }).containsOnly(200)
        val rejected = filter.call()

        assertThat(rejected.status).isEqualTo(429)
        assertThat(rejected.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1")
        assertThat(rejected.contentType).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        val json = JsonMapper().readTree(rejected.contentAsString)
        assertThat(json.path("status").asInt()).isEqualTo(429)
        assertThat(json.path("type").asString()).isEqualTo("urn:mad-mobility:problem:rate-limited")
        assertThat(json.path("instance").asString()).isEqualTo(PING)
        assertThat(passed).hasValue(60)
    }

    @Test
    fun `each IP has its own bucket`() {
        val filter = filter(capacity = 1)

        filter.call()

        assertThat(filter.call().status).isEqualTo(429)
        assertThat(filter.call(ip = OTHER_CLIENT).status).isEqualTo(200)
    }

    @Test
    fun `refills lazily from the clock without exceeding the capacity`() {
        val filter = filter(capacity = 2)
        repeat(2) { filter.call() }

        clock.advance(Duration.ofSeconds(1))
        assertThat(listOf(filter.call().status, filter.call().status)).containsExactly(200, 429)

        clock.advance(Duration.ofHours(1))
        assertThat((1..3).map { filter.call().status }).containsExactly(200, 200, 429)
    }

    @Test
    fun `Retry-After rounds the wait for the next token up to whole seconds`() {
        val filter = filter(capacity = 1)
        filter.call()

        clock.advance(Duration.ofMillis(100))

        assertThat(filter.call().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1")
    }

    @Test
    fun `only limits the public API`() {
        val filter = filter(capacity = 1)

        assertThat((1..5).map { filter.call(path = "/health/live").status }).containsOnly(200)
        assertThat(filter.call().status).isEqualTo(200)
    }

    @Test
    fun `counts rejections without tagging the IP`() {
        val filter = filter(capacity = 1)

        repeat(3) { filter.call() }

        val counter = meterRegistry.get("http.rate_limit.rejected").counter()
        assertThat(counter.count()).isEqualTo(2.0)
        assertThat(counter.id.tags).isEmpty()
    }

    @Test
    fun `concurrent requests from one IP take exactly the capacity`() {
        val filter = filter(capacity = THREADS)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(THREADS)

        val statuses =
            executor.use {
                val futures = (1..THREADS).map { _ -> executor.submit<Int> { start.await().let { filter.call().status } } }
                start.countDown()
                futures.map { it.get() }
            }

        assertThat(statuses).hasSize(THREADS).containsOnly(200)
        assertThat(passed).hasValue(THREADS)
        assertThat(filter.call().status).isEqualTo(429)
    }

    private companion object {
        const val PING = "/v1/test/ping"
        const val CLIENT = "203.0.113.1"
        const val OTHER_CLIENT = "203.0.113.2"
        const val THREADS = 50

        // Like the application's mapper, which writes ProblemDetail properties at the top level
        val PROBLEM_MAPPER: JsonMapper =
            JsonMapper
                .builder()
                .addMixIn(
                    ProblemDetail::class.java,
                    ProblemDetailJacksonMixin::class.java,
                ).build()
    }
}
