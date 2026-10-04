package io.github.jorgetroya80.madmobility.shared.emt

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest
@AutoConfigureMockMvc
class EmtHealthIndicatorTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val circuitBreaker: CircuitBreaker,
) {
    @AfterEach
    fun closeCircuit() = circuitBreaker.transitionToClosedState()

    @Test
    fun `health shows the emt component without secrets`() {
        val result = mvc.get().uri("/health").exchange()

        assertThat(result)
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.components.emt.status")
            .isEqualTo("UP")
        assertThat(result).bodyJson().extractingPath("$.components.emt.details.circuitBreaker").isEqualTo("CLOSED")
        assertThat(result).bodyJson().extractingPath("$.components.emt.details.quotaLimit").isEqualTo(18_000)
        assertThat(result.response.contentAsString).doesNotContain("test-password", "accessToken")
    }

    @Test
    fun `with the EMT down, live and ready stay up and health shows the open circuit`() {
        circuitBreaker.transitionToOpenState()

        assertThat(mvc.get().uri("/health/live")).hasStatusOk()
        assertThat(mvc.get().uri("/health/ready")).hasStatusOk()
        val health = mvc.get().uri("/health").exchange()
        assertThat(health)
            .hasStatus(503)
            .bodyJson()
            .extractingPath("$.components.emt.status")
            .isEqualTo("DOWN")
        assertThat(health).bodyJson().extractingPath("$.components.emt.details.circuitBreaker").isEqualTo("OPEN")
    }
}
