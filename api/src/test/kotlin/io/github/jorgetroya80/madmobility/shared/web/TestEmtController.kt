package io.github.jorgetroya80.madmobility.shared.web

import io.github.jorgetroya80.madmobility.shared.emt.EmtAuthFailed
import io.github.jorgetroya80.madmobility.shared.emt.EmtProtocolError
import io.github.jorgetroya80.madmobility.shared.emt.EmtQuotaExceeded
import io.github.jorgetroya80.madmobility.shared.emt.EmtUnavailable
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration

@ResponseStatus(HttpStatus.NOT_FOUND, reason = "Test resource not found")
class TestNotFound : RuntimeException("not found")

/** Test-only endpoints to exercise shared web infrastructure without domain modules. */
@RestController
class TestEmtController(
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/v1/test/ping")
    fun ping(): String {
        log.info("ping received")
        return "pong"
    }

    @GetMapping("/v1/test/fail/{kind}")
    fun fail(
        @PathVariable kind: String,
    ): String =
        throw when (kind) {
            "timeout" -> EmtUnavailable(EmtUnavailable.Reason.TIMEOUT, "slow")
            "server-error" -> EmtUnavailable(EmtUnavailable.Reason.SERVER_ERROR, "down")
            "connection-failed" -> EmtUnavailable(EmtUnavailable.Reason.CONNECTION_FAILED, "unreachable")
            "circuit-open" -> EmtUnavailable(EmtUnavailable.Reason.CIRCUIT_OPEN, "open")
            "quota" -> EmtQuotaExceeded("bicimad", clock.instant().plus(Duration.ofSeconds(90)))
            "auth" -> EmtAuthFailed("EMT rejected the configured credentials (code=89, description=Invalid user)")
            "not-found" -> TestNotFound()
            "bad-request" -> ResponseStatusException(HttpStatus.BAD_REQUEST, "bad query")
            "protocol" -> EmtProtocolError("42", "Unexpected EMT response (code=42, description=secret internal)")
            else -> IllegalStateException("boom: internal detail")
        }
}
