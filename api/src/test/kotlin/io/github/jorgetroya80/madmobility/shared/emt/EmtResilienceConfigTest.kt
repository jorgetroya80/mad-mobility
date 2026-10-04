package io.github.jorgetroya80.madmobility.shared.emt

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType
import io.github.resilience4j.retry.Retry
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/** The production resilience settings match the spec (SC5: 2 x 5 s read timeout + 0.5 s wait < 12 s). */
@SpringBootTest
class EmtResilienceConfigTest(
    @Autowired private val circuitBreaker: CircuitBreaker,
    @Autowired private val retry: Retry,
    @Autowired private val properties: EmtProperties,
    @Autowired private val meterRegistry: MeterRegistry,
) {
    @Test
    fun `retry makes one extra attempt after 500 ms`() {
        val config = retry.retryConfig

        assertThat(config.maxAttempts).isEqualTo(2)
        assertThat(config.getIntervalBiFunction<Any>().apply(1, null)).isEqualTo(500L)
        assertThat(config.exceptionPredicate.test(EmtUnavailable(EmtUnavailable.Reason.TIMEOUT, "t"))).isTrue()
        assertThat(config.exceptionPredicate.test(EmtProtocolError("42", "p"))).isFalse()
    }

    @Test
    fun `worst case of two timeouts stays under 12 seconds`() {
        val worstCase = properties.readTimeout.multipliedBy(2).plusMillis(500)

        assertThat(worstCase).isLessThan(Duration.ofSeconds(12))
    }

    @Test
    fun `circuit breaker opens at 50 percent of 10 calls for 30 seconds`() {
        val config = circuitBreaker.circuitBreakerConfig

        assertThat(config.slidingWindowType).isEqualTo(SlidingWindowType.COUNT_BASED)
        assertThat(config.slidingWindowSize).isEqualTo(10)
        assertThat(config.minimumNumberOfCalls).isEqualTo(10)
        assertThat(config.failureRateThreshold).isEqualTo(50f)
        assertThat(config.waitIntervalFunctionInOpenState.apply(1)).isEqualTo(30_000L)
        assertThat(config.permittedNumberOfCallsInHalfOpenState).isEqualTo(2)
        assertThat(config.recordExceptionPredicate.test(EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "s"))).isTrue()
        assertThat(config.recordExceptionPredicate.test(EmtAuthFailed("a"))).isFalse()
    }

    @Test
    fun `circuit breaker metrics are published`() {
        assertThat(meterRegistry.find("resilience4j.circuitbreaker.state").tag("name", "emt").meters()).isNotEmpty()
    }
}
