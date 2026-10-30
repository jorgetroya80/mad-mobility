package io.github.jorgetroya80.madtransit.shared.emt

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

class EmtResponseTest {
    private fun description(json: String): String? = jacksonObjectMapper().readValue<EmtResponse<Any>>(json).description

    @Test
    fun `a text description is read as is`() {
        assertThat(description("""{"code":"00","description":"Data recovered OK"}""")).isEqualTo("Data recovered OK")
    }

    @Test
    fun `a list of translations is joined in its order`() {
        val json = """{"code":"80","description":[{"ES":"Parada no disponible"},{"EN":"Bus Stop disabled"}]}"""

        assertThat(description(json)).isEqualTo("Parada no disponible / Bus Stop disabled")
    }

    @Test
    fun `any other description is kept as its JSON text`() {
        assertThat(description("""{"code":"90","description":{"error":1}}""")).isEqualTo("""{"error":1}""")
    }

    @Test
    fun `a missing or null description is null`() {
        assertThat(description("""{"code":"00"}""")).isNull()
        assertThat(description("""{"code":"00","description":null}""")).isNull()
    }
}
