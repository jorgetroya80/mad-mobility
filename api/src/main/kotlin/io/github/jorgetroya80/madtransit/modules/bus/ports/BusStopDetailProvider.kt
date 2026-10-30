package io.github.jorgetroya80.madtransit.modules.bus.ports

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetailSnapshot

/** Source of the detail of one EMT bus stop, by its number. */
interface BusStopDetailProvider {
    fun detail(number: String): BusStopDetailSnapshot
}
