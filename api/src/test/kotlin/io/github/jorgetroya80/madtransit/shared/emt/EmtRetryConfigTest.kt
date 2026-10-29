package io.github.jorgetroya80.madtransit.shared.emt

import io.github.resilience4j.retry.Retry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/** The production resilience settings match the spec (SC5: 2 x 5 s read timeout + 0.5 s wait < 12 s). */
@SpringBootTest
class EmtRetryConfigTest(
    @Autowired private val retry: Retry,
    @Autowired private val properties: EmtProperties,
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
}
