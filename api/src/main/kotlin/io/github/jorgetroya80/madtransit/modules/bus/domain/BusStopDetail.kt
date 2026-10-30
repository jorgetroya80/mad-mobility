package io.github.jorgetroya80.madtransit.modules.bus.domain

import java.time.Instant

/** What the stop list does not say about a stop: its postal [address] and its [lines] with today's schedule. */
data class BusStopDetail(
    val address: String?,
    val lines: List<BusLine>,
)

/**
 * A line at a stop. [direction] is `A` (towards [headerA]) or `B` (towards [headerB]). Today's
 * schedule is null when the EMT has none for today; night lines cross midnight ([firstBus] `23:30`,
 * [lastBus] `05:30`).
 */
data class BusLine(
    val label: String,
    val headerA: String,
    val headerB: String,
    val direction: String,
    val firstBus: String?,
    val lastBus: String?,
    val minFrequencyMinutes: Int?,
    val maxFrequencyMinutes: Int?,
)

/** The detail of one stop as of [updatedAt]; [stale] means the source failed and this is the last known one. */
data class BusStopDetailSnapshot(
    val detail: BusStopDetail,
    val updatedAt: Instant,
    val stale: Boolean,
)
