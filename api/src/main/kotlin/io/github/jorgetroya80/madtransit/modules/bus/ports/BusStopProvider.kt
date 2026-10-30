package io.github.jorgetroya80.madtransit.modules.bus.ports

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot

/** Source of the current EMT bus stops. */
interface BusStopProvider {
    fun snapshot(): BusStopSnapshot
}
