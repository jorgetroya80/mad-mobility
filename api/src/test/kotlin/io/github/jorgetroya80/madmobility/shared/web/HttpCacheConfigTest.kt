package io.github.jorgetroya80.madmobility.shared.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest
@AutoConfigureMockMvc
class HttpCacheConfigTest(
    @Autowired private val mvc: MockMvcTester,
) {
    @Test
    fun `200 responses carry a weak ETag and no-cache`() {
        val result = mvc.get().uri(PING).exchange()

        assertThat(result).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        assertThat(result.response.getHeader(HttpHeaders.ETAG)).startsWith("W/\"")
    }

    @Test
    fun `a matching If-None-Match is a 304 with no body, no-cache and the request id`() {
        val etag =
            checkNotNull(
                mvc
                    .get()
                    .uri(PING)
                    .exchange()
                    .response
                    .getHeader(HttpHeaders.ETAG),
            )

        val result =
            mvc
                .get()
                .uri(PING)
                .header(HttpHeaders.IF_NONE_MATCH, etag)
                .header(RequestIdFilter.HEADER, "req-304")
                .exchange()

        assertThat(result)
            .hasStatus(304)
            .hasHeader(HttpHeaders.ETAG, etag)
            .hasHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
            .hasHeader(RequestIdFilter.HEADER, "req-304")
        assertThat(result.response.contentAsByteArray).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["/v1/test/fail/not-found", "/v1/test/fail/bad-request", "/v1/unknown"])
    fun `problems never get an ETag or a 304`(path: String) {
        val result =
            mvc
                .get()
                .uri(path)
                .header(HttpHeaders.IF_NONE_MATCH, "*")
                .exchange()

        assertThat(result.response.status).isBetween(400, 499)
        assertThat(result.response.getHeader(HttpHeaders.ETAG)).isNull()
    }

    @Test
    fun `health endpoints are untouched`() {
        val result = mvc.get().uri("/health/live").exchange()

        assertThat(result).hasStatusOk()
        assertThat(result.response.getHeader(HttpHeaders.ETAG)).isNull()
        assertThat(result.response.getHeader(HttpHeaders.CACHE_CONTROL)).isNull()
    }

    private companion object {
        const val PING = "/v1/test/ping"
    }
}
