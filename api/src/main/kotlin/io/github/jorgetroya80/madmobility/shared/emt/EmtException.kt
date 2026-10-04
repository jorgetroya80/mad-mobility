package io.github.jorgetroya80.madmobility.shared.emt

import java.time.Instant

/** Expected failures when talking to EMT MobilityLabs. Messages never contain credentials or tokens. */
sealed class EmtException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** EMT rejected the configured credentials (login code 89). */
class EmtAuthFailed(
    message: String,
) : EmtException(message)

/** EMT could not be reached or did not answer properly in time. */
class EmtUnavailable(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : EmtException(message, cause) {
    enum class Reason { TIMEOUT, SERVER_ERROR, CIRCUIT_OPEN }
}

/** The daily EMT call budget of [module] is spent until [resetsAt]; the EMT was not called. */
class EmtQuotaExceeded(
    val module: String,
    val resetsAt: Instant,
) : EmtException("EMT daily quota exhausted for $module until $resetsAt")

/** EMT answered with an unexpected code or an unreadable body. */
class EmtProtocolError(
    val code: String?,
    message: String,
    cause: Throwable? = null,
) : EmtException(message, cause)
