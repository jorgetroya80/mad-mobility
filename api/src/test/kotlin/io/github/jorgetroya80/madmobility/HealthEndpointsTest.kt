package io.github.jorgetroya80.madmobility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointsTest(
    @Autowired private val mvc: MockMvcTester,
) {
    @Test
    fun `liveness is up`() {
        assertThat(mvc.get().uri("/health/live"))
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `readiness is up`() {
        assertThat(mvc.get().uri("/health/ready"))
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `metrics are not exposed over HTTP`() {
        assertThat(mvc.get().uri("/metrics")).hasStatus(HttpStatus.NOT_FOUND)
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.NOT_FOUND)
    }
}
