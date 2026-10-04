package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.assertj.MockMvcTester

/** SC11: a full flow through the Spring context (login, rejected token, relogin, errors) never logs secrets. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class EmtSecretsLeakTest(
    @Autowired private val client: EmtHttpClient,
    @Autowired private val mvc: MockMvcTester,
) : EmtWireMockTest() {
    @Test
    fun `no log line contains the password, pass key or token`(output: CapturedOutput) {
        stubLogin()
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS))
                .inScenario("token")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                    aResponse().withStatus(401).withHeader("Content-Type", "application/json").withBody(fixture("token-invalid.json")),
                ).willSetStateTo("ok"),
        )
        wireMock.stubFor(
            get(urlPathEqualTo(STATIONS))
                .inScenario("token")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(fixture("bicimad-stations.json"))),
        )

        client.get("bicimad", STATIONS, Map::class.java)
        mvc.get().uri("/v1/test/fail/auth").exchange()
        mvc.get().uri("/health").exchange()

        assertThat(output.all).contains("logging in again").doesNotContain(SECRET_PASSWORD, FIXTURE_TOKEN)
    }

    companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
        const val SECRET_PASSWORD = "leak-check-password-4f1c"

        @JvmStatic
        @DynamicPropertySource
        fun emtProperties(registry: DynamicPropertyRegistry) {
            registry.add("mad-mobility.emt.base-url") { wireMock.baseUrl() }
            registry.add("mad-mobility.emt.password") { SECRET_PASSWORD }
        }
    }
}
