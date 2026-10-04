package io.github.jorgetroya80.madmobility.shared.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailsHandlerTest(
    @Autowired private val mvc: MockMvcTester,
) {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "timeout,      504, urn:mad-mobility:problem:emt-timeout,         ''",
        "server-error, 503, urn:mad-mobility:problem:emt-unavailable,     ''",
        "circuit-open, 503, urn:mad-mobility:problem:emt-unavailable,     30",
        "quota,        503, urn:mad-mobility:problem:emt-quota-exhausted, 90",
        "auth,         502, urn:mad-mobility:problem:emt-bad-response,    ''",
        "protocol,     502, urn:mad-mobility:problem:emt-bad-response,    ''",
        "unexpected,   500, urn:mad-mobility:problem:internal,            ''",
        "not-found,    404, about:blank,                                  ''",
    )
    fun `maps errors to standard HTTP problems`(
        kind: String,
        status: Int,
        type: String,
        retryAfter: String,
    ) {
        val result =
            mvc
                .get()
                .uri("/v1/test/fail/$kind")
                .header(RequestIdFilter.HEADER, "req-$kind")
                .exchange()

        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        val body = result.response.contentAsString
        val json = JsonMapper().readTree(body)
        assertThat(json.path("type").asString()).isEqualTo(type)
        assertThat(json.path("status").asInt()).isEqualTo(status)
        assertThat(json.path("instance").asString()).isEqualTo("/v1/test/fail/$kind")
        assertThat(json.path("requestId").asString()).isEqualTo("req-$kind")
        assertThat(body).doesNotContain("code", "89", "42", "secret internal", "internal detail")
        if (retryAfter.isEmpty()) {
            assertThat(result.response.getHeader(HttpHeaders.RETRY_AFTER)).isNull()
        } else {
            assertThat(result.response.getHeader(HttpHeaders.RETRY_AFTER)?.toLong()).isBetween(retryAfter.toLong() - 1, retryAfter.toLong())
        }
    }

    @ParameterizedTest
    @CsvSource("/v1/unknown")
    fun `framework errors are problems with a request id too`(path: String) {
        val result =
            mvc
                .get()
                .uri(path)
                .header(RequestIdFilter.HEADER, "req-404")
                .exchange()

        assertThat(result).hasStatus(404).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(JsonMapper().readTree(result.response.contentAsString).path("requestId").asString()).isEqualTo("req-404")
    }
}
