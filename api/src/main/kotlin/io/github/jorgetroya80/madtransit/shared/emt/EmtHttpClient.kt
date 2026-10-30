package io.github.jorgetroya80.madtransit.shared.emt

import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.ResolvableType
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

/**
 * The only way modules call EMT MobilityLabs: adds the access token, checks the EMT `code`
 * (errors may come with HTTP 200), logs in again once when the token is rejected and maps
 * failures to [EmtException]s.
 */
@Component
class EmtHttpClient(
    private val emtRestClient: RestClient,
    private val emtAuth: EmtAuth,
    private val emtRetry: Retry,
    private val quotaTracker: QuotaTracker,
) {
    /**
     * GETs [path] for [module] and returns the EMT `data` array as [elementType] items.
     * Transport failures are retried once.
     */
    fun <T : Any> get(
        module: String,
        path: String,
        elementType: Class<T>,
    ): List<T> = execute(EmtRequest(module, path, HttpMethod.GET), elementType)

    /**
     * POSTs to [path] for [module], with [body] as JSON when given, and returns the EMT `data`
     * array as [elementType] items. Transport failures are retried once.
     */
    fun <T : Any> post(
        module: String,
        path: String,
        elementType: Class<T>,
        body: Any? = null,
    ): List<T> = execute(EmtRequest(module, path, HttpMethod.POST, body), elementType)

    private fun <T : Any> execute(
        request: EmtRequest,
        elementType: Class<T>,
    ): List<T> = emtRetry.executeSupplier { executeOnce(request, responseType(elementType)) }

    // The EMT answers `code` 80 on HTTP 401 or 200 depending on the endpoint, so only the code decides
    private fun <T> executeOnce(
        request: EmtRequest,
        responseType: ParameterizedTypeReference<EmtResponse<T>>,
    ): List<T> {
        val token = emtAuth.accessToken()
        val (body, status) = call(request, token, responseType)
        if (body?.code != EmtResponse.CODE_TOKEN_INVALID) return dataOrThrow(request, body, status)

        log.info("EMT rejected the access token for {}, logging in again", request)
        emtAuth.invalidate(token)
        val (retryBody, retryStatus) = call(request, emtAuth.accessToken(), responseType)
        if (retryBody?.code == EmtResponse.CODE_TOKEN_INVALID) {
            throw EmtProtocolError(retryBody.code, "EMT rejected a freshly issued access token ($request)")
        }
        return dataOrThrow(request, retryBody, retryStatus)
    }

    private fun <T> call(
        request: EmtRequest,
        token: String,
        responseType: ParameterizedTypeReference<EmtResponse<T>>,
    ): Pair<EmtResponse<T>?, HttpStatusCode> {
        quotaTracker.acquire(request.module)
        return try {
            requestSpec(request)
                .header(ACCESS_TOKEN_HEADER, token)
                .exchange { _, res ->
                    if (res.statusCode.is5xxServerError) {
                        throw EmtUnavailable(
                            EmtUnavailable.Reason.SERVER_ERROR,
                            "EMT answered HTTP ${res.statusCode.value()} for ${request.path}",
                        )
                    }
                    // EMT errors (e.g. invalid token on HTTP 401) carry the JSON envelope; other bodies are not readable
                    EmtErrors.readBody { res.bodyTo(responseType) } to res.statusCode
                }
        } catch (e: ResourceAccessException) {
            throw EmtErrors.unavailable(e)
        }
    }

    private fun requestSpec(request: EmtRequest): RestClient.RequestHeadersSpec<*> {
        val spec = emtRestClient.method(request.method).uri(request.path)
        val body = request.body ?: return spec
        return spec.contentType(MediaType.APPLICATION_JSON).body(body)
    }

    private fun <T> dataOrThrow(
        request: EmtRequest,
        body: EmtResponse<T>?,
        status: HttpStatusCode,
    ): List<T> {
        if (body != null && body.isSuccess) return body.data.orEmpty()
        throw EmtProtocolError(
            body?.code,
            "Unexpected EMT response for $request (HTTP ${status.value()}, code=${body?.code}, description=${body?.description})",
        )
    }

    private fun <T> responseType(elementType: Class<T>): ParameterizedTypeReference<EmtResponse<T>> =
        ParameterizedTypeReference.forType(ResolvableType.forClassWithGenerics(EmtResponse::class.java, elementType).type)

    private data class EmtRequest(
        val module: String,
        val path: String,
        val method: HttpMethod,
        val body: Any? = null,
    ) {
        override fun toString() = "$module $path"
    }

    private companion object {
        val log = LoggerFactory.getLogger(EmtHttpClient::class.java)
        const val ACCESS_TOKEN_HEADER = "accessToken"
    }
}
