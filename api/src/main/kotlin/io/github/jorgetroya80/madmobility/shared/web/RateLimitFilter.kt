package io.github.jorgetroya80.madmobility.shared.web

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

@ConfigurationProperties("mad-mobility.rate-limit")
data class RateLimitProperties(
    /** Requests one IP can make at once. */
    val capacity: Int = 60,
    /** Tokens given back to each IP per second. */
    val refillPerSecond: Int = 1,
)

/**
 * Per-IP token bucket for the public API under /v1: each request takes a token, tokens come back
 * lazily from the [Clock] and an empty bucket gets a 429 problem with Retry-After.
 */
@Component
@Order(RateLimitFilter.ORDER)
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitFilter(
    private val properties: RateLimitProperties,
    private val clock: Clock,
    meterRegistry: MeterRegistry,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {
    private val buckets: Cache<String, AtomicReference<Bucket>> =
        Caffeine
            .newBuilder()
            .expireAfterAccess(IDLE_EXPIRY)
            .maximumSize(MAX_CLIENTS)
            .build()
    private val rejected = meterRegistry.counter(REJECTED_METRIC)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith(API_PREFIX)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val wait = takeToken(request.remoteAddr)
        if (wait == null) {
            filterChain.doFilter(request, response)
            return
        }
        rejected.increment()
        reject(request, response, wait)
    }

    /** Takes a token from [client]'s bucket: null when taken, otherwise the wait until the next token. */
    private fun takeToken(client: String): Duration? {
        val now = clock.instant()
        val bucket = buckets.get(client) { AtomicReference(Bucket(properties.capacity.toDouble(), now)) }
        while (true) {
            val current = bucket.get()
            val refilled = refill(current, now)
            if (refilled.tokens < 1) return waitForNextToken(refilled)
            if (bucket.compareAndSet(current, refilled.copy(tokens = refilled.tokens - 1))) return null
        }
    }

    // A clock going backwards adds no tokens and never moves refilledAt back
    private fun refill(
        bucket: Bucket,
        now: Instant,
    ): Bucket {
        val refilledAt = maxOf(bucket.refilledAt, now)
        val elapsedSeconds = Duration.between(bucket.refilledAt, refilledAt).toNanos() / NANOS_PER_SECOND
        val tokens = minOf(properties.capacity.toDouble(), bucket.tokens + elapsedSeconds * properties.refillPerSecond)
        return Bucket(tokens, refilledAt)
    }

    private fun waitForNextToken(bucket: Bucket): Duration =
        Duration.ofNanos(ceil((1 - bucket.tokens) / properties.refillPerSecond * NANOS_PER_SECOND).toLong())

    private fun reject(
        request: HttpServletRequest,
        response: HttpServletResponse,
        wait: Duration,
    ) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.setHeader(HttpHeaders.RETRY_AFTER, wait.toRetryAfterSeconds().toString())
        jsonMapper.writeValue(response.outputStream, problemDetail(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMITED, request))
    }

    private data class Bucket(
        val tokens: Double,
        val refilledAt: Instant,
    )

    companion object {
        /** Right after RequestIdFilter, so a 429 carries the request id. */
        const val ORDER = Ordered.HIGHEST_PRECEDENCE + 1

        private const val API_PREFIX = "/v1/"
        private const val MAX_CLIENTS = 100_000L
        private const val REJECTED_METRIC = "http.rate_limit.rejected"
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private val IDLE_EXPIRY = Duration.ofMinutes(10)
        private val RATE_LIMITED =
            Problem(
                URI.create("urn:mad-mobility:problem:rate-limited"),
                "Too many requests",
                "Too many requests from this client; retry later.",
            )
    }
}
