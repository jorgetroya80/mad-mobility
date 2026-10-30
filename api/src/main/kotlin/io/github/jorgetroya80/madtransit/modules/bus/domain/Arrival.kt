package io.github.jorgetroya80.madtransit.modules.bus.domain

import java.time.Instant

/**
 * A bus coming to a stop. Without an estimate ([noEstimate]) [minutes], [distanceMeters] and [bus]
 * are null; [bus] is also null when the EMT does not know where the bus is.
 */
data class Arrival(
    val line: String,
    val destination: String,
    val minutes: Int?,
    val noEstimate: Boolean,
    val distanceMeters: Int?,
    val bus: GeoPoint?,
)

/** The next arrivals at one stop as of [updatedAt]; [stale] means the source failed and these are the last known ones. */
data class ArrivalSnapshot(
    val arrivals: List<Arrival>,
    val updatedAt: Instant,
    val stale: Boolean,
)
