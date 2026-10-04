package io.github.jorgetroya80.madmobility.shared.emt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tools.jackson.databind.json.JsonMapper

/** Guards the captured EMT contract documented in src/test/resources/emt/README.md. */
class EmtFixturesTest {
    @ParameterizedTest
    @CsvSource(
        "login-ok.json, 01",
        "login-bad-credentials.json, 89",
        "bicimad-stations.json, 00",
        "token-invalid.json, 80",
    )
    fun `fixture has the documented EMT code`(
        file: String,
        code: String,
    ) {
        val json = JsonMapper().readTree(javaClass.getResourceAsStream("/emt/$file"))

        assertThat(json.path("code").asString()).isEqualTo(code)
        assertThat(json.path("data").isArray).isTrue()
    }
}
