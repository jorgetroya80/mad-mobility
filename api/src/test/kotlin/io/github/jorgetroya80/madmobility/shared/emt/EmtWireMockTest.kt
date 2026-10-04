package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Base for tests that need real HTTP against a fake EMT (in-process WireMock, reset between tests). */
abstract class EmtWireMockTest {
    // Production timeouts: the read timeout also covers reading the body, so short values make slow CI flaky
    protected val properties = EmtProperties(email = TEST_EMAIL, password = TEST_PASSWORD)

    protected fun restClient(props: EmtProperties = properties): RestClient =
        EmtConfig.emtRestClient(RestClient.builder(), wireMock.baseUrl(), props)

    protected fun stubLogin(
        fixture: String = "login-ok.json",
        delay: Duration = Duration.ZERO,
    ) {
        wireMock.stubFor(
            get(urlPathEqualTo(LOGIN_PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(fixture(fixture))
                    .withFixedDelay(delay.toMillis().toInt()),
            ),
        )
    }

    protected fun fixture(name: String): String = requireNotNull(javaClass.getResource("/emt/$name")) { "missing fixture $name" }.readText()

    /** Clock that tests move forward explicitly. */
    class MutableClock(
        var now: Instant = Instant.parse("2026-10-04T10:00:00Z"),
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Europe/Madrid")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }

    companion object {
        const val LOGIN_PATH = "/v2/mobilitylabs/user/login/"
        const val TEST_EMAIL = "tester@example.com"
        const val TEST_PASSWORD = "super-secret-password"

        /** Access token in login-ok.json (fake value). */
        const val FIXTURE_TOKEN = "00000000-0000-0000-0000-00000000a11d"

        @JvmField
        @RegisterExtension
        val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(wireMockConfig().dynamicPort())
                .build()
    }
}
