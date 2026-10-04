package io.github.jorgetroya80.madmobility.shared.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult

private fun MockMvcTester.preflight(origin: String): MvcTestResult =
    options()
        .uri("/v1/test/ping")
        .header(HttpHeaders.ORIGIN, origin)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()

class CorsConfigTest {
    @Nested
    @SpringBootTest(properties = ["mad-mobility.cors.allowed-origins=https://allowed.example"])
    @AutoConfigureMockMvc
    inner class WithAllowedOrigins(
        @Autowired private val mvc: MockMvcTester,
    ) {
        @Test
        fun `preflight from an allowed origin gets CORS headers`() {
            assertThat(mvc.preflight("https://allowed.example"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://allowed.example")
        }

        @Test
        fun `allowed origin can read ETag and X-Request-Id`() {
            val result =
                mvc
                    .get()
                    .uri("/v1/test/ping")
                    .header(HttpHeaders.ORIGIN, "https://allowed.example")
                    .exchange()

            assertThat(result.response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains(HttpHeaders.ETAG, RequestIdFilter.HEADER)
        }

        @Test
        fun `preflight from another origin is rejected`() {
            val result = mvc.preflight("https://evil.example")

            assertThat(result).hasStatus(403)
            assertThat(result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    inner class WithoutOrigins(
        @Autowired private val mvc: MockMvcTester,
    ) {
        @Test
        fun `no origin gets CORS headers`() {
            val result = mvc.preflight("https://allowed.example")

            assertThat(result.response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull()
        }
    }
}
