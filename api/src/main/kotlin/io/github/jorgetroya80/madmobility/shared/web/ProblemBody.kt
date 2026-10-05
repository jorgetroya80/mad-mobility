package io.github.jorgetroya80.madmobility.shared.web

import io.swagger.v3.oas.annotations.media.Schema
import java.net.URI

/**
 * OpenAPI shape of every error body: the [org.springframework.http.ProblemDetail] the handlers return,
 * with `requestId` at the top level. Documentation only, never instantiated.
 */
@Schema(name = "Problem", requiredProperties = ["type", "title", "status", "instance", "requestId"])
data class ProblemBody(
    val type: URI,
    val title: String,
    val status: Int,
    val detail: String?,
    val instance: URI,
    val requestId: String,
)
