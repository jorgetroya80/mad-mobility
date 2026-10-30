package io.github.jorgetroya80.madtransit.modules.bus.application

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetail
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopDetailProvider
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.Instant

/**
 * The stop with the given number from the cached stop list, with its detail. A number that is not in
 * the list is not found without asking the EMT, so an invented number spends no quota.
 */
@Service
class GetStop(
    private val stops: BusStopProvider,
    private val details: BusStopDetailProvider,
) {
    // Name and position come from the list, so the result is as old and as stale as the older of both
    operator fun invoke(number: String): FoundStop {
        val snapshot = stops.snapshot()
        val stop = snapshot.find(number) ?: throw BusStopNotFound(number)
        val detail = details.detail(number)
        return FoundStop(
            stop = stop,
            detail = detail.detail,
            updatedAt = minOf(snapshot.updatedAt, detail.updatedAt),
            stale = snapshot.stale || detail.stale,
        )
    }
}

data class FoundStop(
    val stop: BusStop,
    val detail: BusStopDetail,
    val updatedAt: Instant,
    val stale: Boolean,
)

@ResponseStatus(HttpStatus.NOT_FOUND)
class BusStopNotFound(
    number: String,
) : RuntimeException("Bus stop $number not found")
