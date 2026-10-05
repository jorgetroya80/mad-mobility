package io.github.jorgetroya80.madmobility.modules.bicimad.domain

import java.time.Instant

/** All stations as of [updatedAt]; [stale] means the source failed and these are the last known ones. */
data class StationSnapshot(
    val stations: List<Station>,
    val updatedAt: Instant,
    val stale: Boolean,
)
