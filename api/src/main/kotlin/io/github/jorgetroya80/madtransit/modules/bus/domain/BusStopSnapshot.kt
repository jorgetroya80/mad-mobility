package io.github.jorgetroya80.madtransit.modules.bus.domain

import java.time.Instant

/** All stops as of [updatedAt]; [stale] means the source failed and these are the last known ones. */
data class BusStopSnapshot(
    val stops: List<BusStop>,
    val updatedAt: Instant,
    val stale: Boolean,
)
