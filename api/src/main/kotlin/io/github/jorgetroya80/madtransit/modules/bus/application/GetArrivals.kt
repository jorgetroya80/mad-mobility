package io.github.jorgetroya80.madtransit.modules.bus.application

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.ArrivalProvider
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import org.springframework.stereotype.Service

/**
 * The next arrivals at the stop with the given number, soonest first. A number that is not in the
 * cached stop list is not found without asking the EMT: it would answer `code` 80, like a bad token.
 */
@Service
class GetArrivals(
    private val stops: BusStopProvider,
    private val arrivals: ArrivalProvider,
) {
    // Only the arrivals are returned, so freshness is theirs and not the stop list's
    operator fun invoke(number: String): ArrivalSnapshot {
        if (stops.snapshot().find(number) == null) throw BusStopNotFound(number)
        val snapshot = arrivals.arrivals(number)
        return snapshot.copy(arrivals = snapshot.arrivals.sortedWith(SOONEST_FIRST))
    }

    private companion object {
        // Labels that are numbers (`5`, `001`) by value, like stop numbers, before the rest (`C03`, `M3`, `N1`) by text
        val SOONEST_FIRST: Comparator<Arrival> =
            compareBy<Arrival, Int?>(nullsLast()) { it.minutes }
                .thenBy { it.line.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.line }
    }
}
