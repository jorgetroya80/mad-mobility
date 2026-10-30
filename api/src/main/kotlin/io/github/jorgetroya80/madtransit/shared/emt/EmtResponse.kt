package io.github.jorgetroya80.madtransit.shared.emt

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize

/** EMT response envelope: errors may come with HTTP 200, so `code` must always be checked. */
data class EmtResponse<T>(
    val code: String?,
    @JsonDeserialize(using = EmtDescriptionDeserializer::class)
    val description: String?,
    val data: List<T>?,
) {
    val isSuccess: Boolean get() = code in SUCCESS_CODES

    companion object {
        // 00: OK, 01: OK (login extended an existing token)
        val SUCCESS_CODES = setOf("00", "01")
        const val CODE_INVALID_CREDENTIALS = "89"
        const val CODE_TOKEN_INVALID = "80"
    }
}

/**
 * Usually text, but bus arrivals send a list of translations (`[{"ES": …}, {"EN": …}]`), which is
 * joined into one text so the `code` is not lost to a parse error. Anything else is kept as JSON.
 */
internal class EmtDescriptionDeserializer : ValueDeserializer<String>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext,
    ): String {
        val node: JsonNode = ctxt.readTree(p)
        return when {
            node.isString -> node.asString()
            node.isTranslations() -> node.values().flatMap { it.values() }.joinToString(" / ") { it.asString() }
            else -> node.toString()
        }
    }

    private fun JsonNode.isTranslations(): Boolean = isArray && values().all { it.isObject && it.size() == 1 }
}
