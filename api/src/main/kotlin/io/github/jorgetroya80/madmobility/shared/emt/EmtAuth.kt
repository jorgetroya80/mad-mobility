package io.github.jorgetroya80.madmobility.shared.emt

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Logs in to EMT MobilityLabs and caches the access token until shortly before it expires.
 * Concurrent callers that need a token share a single login (single-flight) and its result or
 * failure. Rejected credentials are remembered for a minute so the EMT is not asked again on
 * every request (which would spend quota and could lock the account).
 */
@Component
class EmtAuth(
    private val emtRestClient: RestClient,
    private val properties: EmtProperties,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
    private val quotaTracker: QuotaTracker,
) {
    private data class Token(
        val value: String,
        val renewAt: Instant,
    )

    private data class AuthFailure(
        val error: EmtAuthFailed,
        val until: Instant,
    )

    private val token = AtomicReference<Token?>()
    private val logins = SingleFlight<Token>()

    @Volatile private var authFailure: AuthFailure? = null

    fun accessToken(): String {
        validToken()?.let { return it.value }
        authFailure?.takeIf { clock.instant().isBefore(it.until) }?.let { throw it.error }
        // Another caller may have logged in between our check and taking ownership
        return logins.run(LOGIN_KEY) { validToken() ?: renewToken() }.value
    }

    /** When the cached token will be renewed, or null if there is no valid token (for health details). */
    fun tokenRenewsAt(): Instant? = validToken()?.renewAt

    /** Drops [rejected] if it is still the cached token, so a token renewed meanwhile is kept. */
    fun invalidate(rejected: String) {
        token.updateAndGet { if (it?.value == rejected) null else it }
    }

    private fun validToken(): Token? = token.get()?.takeIf { clock.instant().isBefore(it.renewAt) }

    private fun renewToken(): Token =
        try {
            login().also { token.set(it) }.also { authFailure = null }
        } catch (e: EmtAuthFailed) {
            authFailure = AuthFailure(e, clock.instant().plus(AUTH_FAILURE_BACKOFF))
            throw e
        }

    private fun login(): Token {
        if (!quotaTracker.tryAcquire(QUOTA_MODULE)) throw EmtQuotaExceeded(QUOTA_MODULE, quotaTracker.resetsAt())
        val (body, status) = fetchLogin()
        if (status.is5xxServerError) {
            record("unavailable")
            throw EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "EMT login failed with HTTP ${status.value()}")
        }
        val login = body?.takeIf { it.isSuccess }?.data?.firstOrNull()
        if (login == null) {
            record("rejected")
            throw rejectedLoginError(body, status)
        }
        record("success")
        login.apiCounter?.current?.let(quotaTracker::reportEmtUsage)
        return toToken(login)
    }

    private fun fetchLogin(): Pair<EmtResponse<LoginData>?, HttpStatusCode> =
        try {
            emtRestClient
                .get()
                .uri(LOGIN_PATH)
                .headers(::addCredentials)
                .exchange { _, res ->
                    // 5xx bodies are not EMT JSON; anything else carries the EMT envelope
                    val body = if (res.statusCode.is5xxServerError) null else EmtErrors.readBody { res.bodyTo(LOGIN_RESPONSE) }
                    body to res.statusCode
                }
        } catch (e: ResourceAccessException) {
            record("unavailable")
            throw EmtErrors.unavailable(e)
        }

    private fun rejectedLoginError(
        body: EmtResponse<LoginData>?,
        status: HttpStatusCode,
    ): EmtException {
        val detail = "code=${body?.code}, description=${body?.description}"
        if (body?.code != EmtResponse.CODE_INVALID_CREDENTIALS) {
            return EmtProtocolError(body?.code, "Unexpected EMT login response (HTTP ${status.value()}, $detail)")
        }
        log.error("EMT rejected the configured credentials ({})", detail)
        return EmtAuthFailed("EMT rejected the configured credentials ($detail)")
    }

    private fun toToken(login: LoginData): Token {
        val lifetime = Duration.ofSeconds(login.tokenSecExpiration)
        // Renew 5 min early, or halfway for short-lived tokens, so a token is always reused for a while
        val renewAt = clock.instant().plus(lifetime).minus(minOf(RENEW_MARGIN, lifetime.dividedBy(2)))
        log.info("EMT login OK, token renews at {}", renewAt)
        return Token(login.accessToken, renewAt)
    }

    private fun addCredentials(headers: HttpHeaders) {
        when (val credentials = properties.credentials) {
            is EmtCredentials.EmailPassword -> {
                headers.set(EMAIL_HEADER, credentials.email)
                headers.set(PASSWORD_HEADER, credentials.password)
            }

            is EmtCredentials.ClientKey -> {
                headers.set(CLIENT_ID_HEADER, credentials.clientId)
                headers.set(PASS_KEY_HEADER, credentials.passKey)
            }
        }
    }

    private fun record(result: String) = meterRegistry.counter("emt.auth.logins", "result", result).increment()

    private data class LoginData(
        val accessToken: String,
        val tokenSecExpiration: Long,
        val apiCounter: ApiCounter? = null,
    )

    private data class ApiCounter(
        val current: Long?,
    )

    companion object {
        private val log = LoggerFactory.getLogger(EmtAuth::class.java)

        /** Logins consume EMT quota too, accounted under this module name. */
        const val QUOTA_MODULE = "auth"
        private const val LOGIN_KEY = "token"
        private const val LOGIN_PATH = "/v2/mobilitylabs/user/login/"
        private const val EMAIL_HEADER = "email"
        private const val PASSWORD_HEADER = "password"
        private const val CLIENT_ID_HEADER = "X-ClientId"
        private const val PASS_KEY_HEADER = "passKey"
        private val RENEW_MARGIN: Duration = Duration.ofMinutes(5)
        private val AUTH_FAILURE_BACKOFF: Duration = Duration.ofMinutes(1)
        private val LOGIN_RESPONSE = object : ParameterizedTypeReference<EmtResponse<LoginData>>() {}
    }
}
