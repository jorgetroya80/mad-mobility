package io.github.jorgetroya80.madmobility.shared.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class RequestIdFilterTest(
    @Autowired private val mvc: MockMvcTester,
) {
    @Test
    fun `generates a request id when none is sent`() {
        val result = mvc.get().uri("/v1/test/ping").exchange()

        val requestId = result.response.getHeader(RequestIdFilter.HEADER)
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId)
    }

    @Test
    fun `echoes a valid request id`() {
        assertThat(mvc.get().uri("/v1/test/ping").header(RequestIdFilter.HEADER, "abc-123"))
            .hasHeader(RequestIdFilter.HEADER, "abc-123")
    }

    @Test
    fun `replaces an invalid request id`() {
        listOf("bad id!", "a".repeat(65), "").forEach { invalid ->
            val result =
                mvc
                    .get()
                    .uri("/v1/test/ping")
                    .header(RequestIdFilter.HEADER, invalid)
                    .exchange()

            assertThat(result.response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo(invalid).hasSize(36)
        }
    }

    @Test
    fun `logs are JSON and carry the request id`(output: CapturedOutput) {
        mvc
            .get()
            .uri("/v1/test/ping")
            .header(RequestIdFilter.HEADER, "log-test-42")
            .exchange()

        val line = output.out.lines().single { it.contains("ping received") }
        val json = JsonMapper().readTree(line)
        assertThat(json.path("requestId").asString()).isEqualTo("log-test-42")
        assertThat(json.path("message").asString()).isEqualTo("ping received")
    }
}
