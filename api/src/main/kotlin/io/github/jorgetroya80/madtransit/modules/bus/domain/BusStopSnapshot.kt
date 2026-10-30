package io.github.jorgetroya80.madtransit.modules.bus.domain

import java.time.Instant

/** All stops as of [updatedAt]; [stale] means the source failed and these are the last known ones. */
data class BusStopSnapshot(
    val index: BusStopIndex,
    val updatedAt: Instant,
    val stale: Boolean,
) {
    val stops: List<BusStop> get() = index.stops

    fun find(number: String): BusStop? = index.find(number)
}
