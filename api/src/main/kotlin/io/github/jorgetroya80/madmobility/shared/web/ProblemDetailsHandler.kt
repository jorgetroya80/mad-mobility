package io.github.jorgetroya80.madmobility.shared.web

import io.github.jorgetroya80.madmobility.shared.emt.EmtAuthFailed
import io.github.jorgetroya80.madmobility.shared.emt.EmtException
import io.github.jorgetroya80.madmobility.shared.emt.EmtProtocolError
import io.github.jorgetroya80.madmobility.shared.emt.EmtQuotaExceeded
import io.github.jorgetroya80.madmobility.shared.emt.EmtUnavailable
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.util.DisconnectedClientHelper
import java.net.URI
import java.time.Clock
import java.time.Duration

/**
 * RFC 9457 errors (application/problem+json) with standard HTTP codes only: EMT codes and messages
 * stay in the logs, never in the response. Every problem carries the request id.
 */
@RestControllerAdvice
class ProblemDetailsHandler(
    private val emtCircuitBreaker: CircuitBreaker,
    private val clock: Clock,
) : ResponseEntityExceptionHandler() {
    @ExceptionHandler(EmtException::class)
    fun handleEmt(
        e: EmtException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
        log.warn("EMT failure on {}: {}", request.requestURI, e.message)
        val (status, problem) = statusAndProblem(e)
        return ResponseEntity
            .status(status)
            .apply { retryAfter(e)?.let { header(HttpHeaders.RETRY_AFTER, it.toSecondsCeil().toString()) } }
            .body(problem(status, problem, request))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        e: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail>? {
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            log.debug("Client disconnected on {}: {}", request.requestURI, e.message)
            return null
        }
        // Module exceptions annotated with @ResponseStatus (e.g. 404) keep their status
        AnnotatedElementUtils.findMergedAnnotation(e.javaClass, ResponseStatus::class.java)?.let { annotation ->
            val status = HttpStatus.valueOf(annotation.code.value())
            val detail = annotation.reason.ifBlank { status.reasonPhrase }
            return ResponseEntity
                .status(status)
                .body(problem(status, Problem(URI.create("about:blank"), status.reasonPhrase, detail), request))
        }
        log.error("Unexpected error on {}", request.requestURI, e)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, request))
    }

    /** Adds the request id to the problems Spring MVC builds for its own exceptions (404, 405, ...). */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        // The body may only be built inside super (from the ErrorResponse), so decorate the result
        super.handleExceptionInternal(ex, body, headers, statusCode, request)?.also {
            (it.body as? ProblemDetail)?.setProperty(REQUEST_ID, MDC.get(RequestIdFilter.MDC_KEY))
        }

    private fun statusAndProblem(e: EmtException): Pair<HttpStatus, Problem> =
        when (e) {
            is EmtUnavailable -> {
                when (e.reason) {
                    EmtUnavailable.Reason.TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT to EMT_TIMEOUT

                    EmtUnavailable.Reason.SERVER_ERROR,
                    EmtUnavailable.Reason.CONNECTION_FAILED,
                    EmtUnavailable.Reason.CIRCUIT_OPEN,
                    -> HttpStatus.SERVICE_UNAVAILABLE to EMT_UNAVAILABLE
                }
            }

            is EmtQuotaExceeded -> {
                HttpStatus.SERVICE_UNAVAILABLE to QUOTA_EXHAUSTED
            }

            is EmtAuthFailed, is EmtProtocolError -> {
                HttpStatus.BAD_GATEWAY to EMT_BAD_RESPONSE
            }
        }

    private fun retryAfter(e: EmtException): Duration? =
        when {
            e is EmtQuotaExceeded -> {
                Duration.between(clock.instant(), e.resetsAt)
            }

            e is EmtUnavailable && e.reason == EmtUnavailable.Reason.CIRCUIT_OPEN -> {
                Duration.ofMillis(emtCircuitBreaker.circuitBreakerConfig.waitIntervalFunctionInOpenState.apply(FIRST_OPEN_ATTEMPT))
            }

            else -> {
                null
            }
        }

    private fun problem(
        status: HttpStatus,
        problem: Problem,
        request: HttpServletRequest,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, problem.detail).apply {
            type = problem.type
            title = problem.title
            instance = URI.create(request.requestURI)
            setProperty(REQUEST_ID, MDC.get(RequestIdFilter.MDC_KEY))
        }

    private fun Duration.toSecondsCeil(): Long = maxOf(1, Math.ceilDiv(toMillis(), MILLIS_PER_SECOND))

    private data class Problem(
        val type: URI,
        val title: String,
        val detail: String,
    )

    private companion object {
        val log = LoggerFactory.getLogger(ProblemDetailsHandler::class.java)
        const val REQUEST_ID = "requestId"
        const val FIRST_OPEN_ATTEMPT = 1
        const val MILLIS_PER_SECOND = 1000L

        val EMT_TIMEOUT = Problem(URI.create("urn:mad-mobility:problem:emt-timeout"), "EMT timeout", "EMT Madrid did not answer in time.")
        val EMT_UNAVAILABLE =
            Problem(URI.create("urn:mad-mobility:problem:emt-unavailable"), "EMT unavailable", "EMT Madrid is not available right now.")
        val QUOTA_EXHAUSTED =
            Problem(
                URI.create("urn:mad-mobility:problem:emt-quota-exhausted"),
                "EMT quota exhausted",
                "The daily EMT Madrid quota is exhausted.",
            )
        val EMT_BAD_RESPONSE =
            Problem(URI.create("urn:mad-mobility:problem:emt-bad-response"), "EMT error", "EMT Madrid returned an unexpected response.")
        val INTERNAL_ERROR = Problem(URI.create("urn:mad-mobility:problem:internal"), "Internal error", "An unexpected error occurred.")
    }
}
