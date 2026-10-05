package io.github.jorgetroya80.madmobility.shared.emt

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.ResolvableType
import org.springframework.http.HttpStatusCode
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
    private val emtCircuitBreaker: CircuitBreaker,
    private val emtRetry: Retry,
    private val quotaTracker: QuotaTracker,
) {
    /**
     * GETs [path] for [module] and returns the EMT `data` array as [elementType] items.
     * Transport failures are retried once; repeated failures open the circuit breaker, which then
     * fails fast with [EmtUnavailable.Reason.CIRCUIT_OPEN] without calling the EMT.
     */
    fun <T : Any> get(
        module: String,
        path: String,
        elementType: Class<T>,
    ): List<T> =
        try {
            emtCircuitBreaker.executeSupplier { emtRetry.executeSupplier { getOnce(module, path, elementType) } }
        } catch (e: CallNotPermittedException) {
            throw EmtUnavailable(EmtUnavailable.Reason.CIRCUIT_OPEN, "EMT circuit breaker is open, not calling $module $path", e)
        }

    private fun <T : Any> getOnce(
        module: String,
        path: String,
        elementType: Class<T>,
    ): List<T> {
        val responseType = responseType(elementType)
        val token = emtAuth.accessToken()
        val (body, status) = call(module, path, token, responseType)
        if (body?.code != EmtResponse.CODE_TOKEN_INVALID) return dataOrThrow(module, path, body, status)

        log.info("EMT rejected the access token for {} {}, logging in again", module, path)
        emtAuth.invalidate(token)
        val (retryBody, retryStatus) = call(module, path, emtAuth.accessToken(), responseType)
        if (retryBody?.code == EmtResponse.CODE_TOKEN_INVALID) {
            throw EmtProtocolError(retryBody.code, "EMT rejected a freshly issued access token ($module $path)")
        }
        return dataOrThrow(module, path, retryBody, retryStatus)
    }

    private fun <T> call(
        module: String,
        path: String,
        token: String,
        responseType: ParameterizedTypeReference<EmtResponse<T>>,
    ): Pair<EmtResponse<T>?, HttpStatusCode> {
        if (!quotaTracker.tryAcquire(module)) throw EmtQuotaExceeded(module, quotaTracker.resetsAt())
        return try {
            emtRestClient
                .get()
                .uri(path)
                .header(ACCESS_TOKEN_HEADER, token)
                .exchange { _, res ->
                    if (res.statusCode.is5xxServerError) {
                        throw EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT answered HTTP ${res.statusCode.value()} for $path")
                    }
                    // EMT errors (e.g. invalid token on HTTP 401) carry the JSON envelope; other bodies are not readable
                    EmtErrors.readBody { res.bodyTo(responseType) } to res.statusCode
                }
        } catch (e: ResourceAccessException) {
            throw EmtErrors.unavailable(e)
        }
    }

    private fun <T> dataOrThrow(
        module: String,
        path: String,
        body: EmtResponse<T>?,
        status: HttpStatusCode,
    ): List<T> {
        if (body != null && body.isSuccess) return body.data.orEmpty()
        throw EmtProtocolError(
            body?.code,
            "Unexpected EMT response for $module $path (HTTP ${status.value()}, code=${body?.code}, description=${body?.description})",
        )
    }

    private fun <T> responseType(elementType: Class<T>): ParameterizedTypeReference<EmtResponse<T>> =
        ParameterizedTypeReference.forType(ResolvableType.forClassWithGenerics(EmtResponse::class.java, elementType).type)

    private companion object {
        val log = LoggerFactory.getLogger(EmtHttpClient::class.java)
        const val ACCESS_TOKEN_HEADER = "accessToken"
    }
}
