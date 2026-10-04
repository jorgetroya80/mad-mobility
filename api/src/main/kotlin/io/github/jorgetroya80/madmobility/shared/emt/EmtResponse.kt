package io.github.jorgetroya80.madmobility.shared.emt

/** EMT response envelope: errors may come with HTTP 200, so `code` must always be checked. */
data class EmtResponse<T>(
    val code: String?,
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
