package io.github.jorgetroya80.madtransit.shared.emt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest
@AutoConfigureMockMvc
class EmtHealthIndicatorTest(
    @Autowired private val mvc: MockMvcTester,
) {
    @Test
    fun `health shows the emt component without secrets`() {
        val result = mvc.get().uri("/health").exchange()

        assertThat(result)
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.components.emt.status")
            .isEqualTo("UP")
        assertThat(result).bodyJson().extractingPath("$.components.emt.details.quotaLimit").isEqualTo(225_000)
        assertThat(result.response.contentAsString).doesNotContain("test-password", "accessToken")
    }
}
