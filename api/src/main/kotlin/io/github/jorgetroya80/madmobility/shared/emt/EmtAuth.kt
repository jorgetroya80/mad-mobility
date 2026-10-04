package io.github.jorgetroya80.madmobility.shared.emt

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Logs in to EMT MobilityLabs and caches the access token until shortly before it expires.
 * Concurrent callers that need a token share a single login (single-flight).
 */
@Component
class EmtAuth(
    private val emtRestClient: RestClient,
    private val properties: EmtProperties,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    private data class Token(
        val value: String,
        val renewAt: Instant,
    )

    @Volatile private var token: Token? = null
    private val loginLock = ReentrantLock()

    fun accessToken(): String {
        validToken()?.let { return it }
        return loginLock.withLock {
            validToken() ?: login().also { token = it }.value
        }
    }

    /** Drops [rejected] if it is still the cached token, so a token renewed meanwhile is kept. */
    fun invalidate(rejected: String) {
        loginLock.withLock {
            if (token?.value == rejected) token = null
        }
    }

    private fun validToken(): String? = token?.takeIf { clock.instant().isBefore(it.renewAt) }?.value

    private fun login(): Token {
        val response =
            try {
                emtRestClient
                    .get()
                    .uri(LOGIN_PATH)
                    .headers(::addCredentials)
                    .exchange { _, res ->
                        // 5xx bodies are not EMT JSON; anything else carries the EMT envelope
                        val body = if (res.statusCode.is5xxServerError) null else readBody { res.bodyTo(LOGIN_RESPONSE) }
                        body to res.statusCode
                    }
            } catch (e: ResourceAccessException) {
                record("unavailable")
                throw unavailable(e)
            }
        val (body, status) = response
        if (status.is5xxServerError) {
            record("unavailable")
            throw EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT login failed with HTTP ${status.value()}")
        }
        val login = body?.takeIf { it.isSuccess }?.data?.firstOrNull()
        if (login == null) {
            record("rejected")
            val detail = "code=${body?.code}, description=${body?.description}"
            if (body?.code == EmtResponse.CODE_INVALID_CREDENTIALS) {
                log.error("EMT rejected the configured credentials ({})", detail)
                throw EmtAuthFailed("EMT rejected the configured credentials ($detail)")
            }
            throw EmtProtocolError(body?.code, "Unexpected EMT login response (HTTP ${status.value()}, $detail)")
        }
        record("success")
        val renewAt = clock.instant().plusSeconds(login.tokenSecExpiration).minus(RENEW_MARGIN)
        log.info("EMT login OK, token renews at {}", renewAt)
        return Token(login.accessToken, renewAt)
    }

    private fun addCredentials(headers: HttpHeaders) {
        when (val credentials = properties.credentials) {
            is EmtCredentials.EmailPassword -> {
                headers.set("email", credentials.email)
                headers.set("password", credentials.password)
            }

            is EmtCredentials.ClientKey -> {
                headers.set("X-ClientId", credentials.clientId)
                headers.set("passKey", credentials.passKey)
            }
        }
    }

    private fun record(result: String) = meterRegistry.counter("emt.auth.logins", "result", result).increment()

    private data class LoginData(
        val accessToken: String,
        val tokenSecExpiration: Long,
    )

    companion object {
        private val log = LoggerFactory.getLogger(EmtAuth::class.java)
        private const val LOGIN_PATH = "/v2/mobilitylabs/user/login/"
        private val RENEW_MARGIN: Duration = Duration.ofMinutes(5)
        private val LOGIN_RESPONSE = object : ParameterizedTypeReference<EmtResponse<LoginData>>() {}

        internal fun <T> readBody(read: () -> T): T =
            try {
                read()
            } catch (e: RestClientException) {
                // An I/O error while reading the body (dropped connection, read timeout) is a transport
                // failure; anything else (malformed JSON) is a protocol error
                if (generateSequence<Throwable>(e) { it.cause }.any { it is IOException }) {
                    throw EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT connection failed while reading the response", e)
                }
                throw EmtProtocolError(null, "Unreadable EMT response: ${e.mostSpecificCause.message}", e)
            }

        internal fun unavailable(e: ResourceAccessException): EmtUnavailable {
            val timedOut = generateSequence<Throwable>(e) { it.cause }.any { it is HttpTimeoutException || it is SocketTimeoutException }
            return if (timedOut) {
                EmtUnavailable(EmtUnavailable.Reason.TIMEOUT, "EMT did not answer in time", e)
            } else {
                EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT could not be reached", e)
            }
        }
    }
}
