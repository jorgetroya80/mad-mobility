package io.github.jorgetroya80.madmobility.shared.emt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class EmtPropertiesTest {
    @EnableConfigurationProperties(EmtProperties::class)
    class PropertiesOnly

    private val runner = ApplicationContextRunner().withConfiguration(UserConfigurations.of(PropertiesOnly::class.java))

    private fun failureMessage(vararg properties: String): String? {
        var message: String? = null
        runner.withPropertyValues(*properties).run { context ->
            assertThat(context).hasFailed()
            message = generateSequence(context.startupFailure) { it.cause }.joinToString(" | ") { it.message.orEmpty() }
        }
        return message
    }

    @Test
    fun `fails naming both options when no credentials are set`() {
        assertThat(failureMessage())
            .contains("EMT_EMAIL", "EMT_PASSWORD", "EMT_CLIENT_ID", "EMT_PASS_KEY")
    }

    @Test
    fun `fails naming the missing half of a pair`() {
        assertThat(failureMessage("mad-mobility.emt.email=a@b.c")).contains("EMT_PASSWORD is missing")
        assertThat(failureMessage("mad-mobility.emt.password=x")).contains("EMT_EMAIL is missing")
        assertThat(failureMessage("mad-mobility.emt.client-id=id")).contains("EMT_PASS_KEY is missing")
        assertThat(failureMessage("mad-mobility.emt.pass-key=k")).contains("EMT_CLIENT_ID is missing")
    }

    @Test
    fun `blank values count as missing`() {
        assertThat(failureMessage("mad-mobility.emt.email=a@b.c", "mad-mobility.emt.password= "))
            .contains("EMT_PASSWORD is missing")
    }

    @Test
    fun `uses email and password`() {
        runner.withPropertyValues("mad-mobility.emt.email=a@b.c", "mad-mobility.emt.password=secret").run { context ->
            assertThat(context.getBean(EmtProperties::class.java).credentials)
                .isEqualTo(EmtCredentials.EmailPassword("a@b.c", "secret"))
        }
    }

    @Test
    fun `client key wins when both pairs are set`() {
        runner
            .withPropertyValues(
                "mad-mobility.emt.email=a@b.c",
                "mad-mobility.emt.password=secret",
                "mad-mobility.emt.client-id=id",
                "mad-mobility.emt.pass-key=key",
            ).run { context ->
                assertThat(context.getBean(EmtProperties::class.java).credentials)
                    .isEqualTo(EmtCredentials.ClientKey("id", "key"))
            }
    }

    @Test
    fun `defaults match the spec`() {
        runner.withPropertyValues("mad-mobility.emt.email=a@b.c", "mad-mobility.emt.password=secret").run { context ->
            val properties = context.getBean(EmtProperties::class.java)
            assertThat(properties.baseUrl.toString()).isEqualTo("https://openapi.emtmadrid.es")
            assertThat(properties.connectTimeout.seconds).isEqualTo(2)
            assertThat(properties.readTimeout.seconds).isEqualTo(5)
        }
    }

    @Test
    fun `toString never reveals credentials`() {
        val properties = EmtProperties(email = "user@mail.com", password = "s3cret")

        assertThat(properties.toString()).doesNotContain("user@mail.com", "s3cret")
        assertThat(EmtProperties(clientId = "my-id", passKey = "my-key").toString()).doesNotContain("my-id", "my-key")
    }
}
