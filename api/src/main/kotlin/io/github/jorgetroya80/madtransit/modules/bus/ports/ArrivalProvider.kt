package io.github.jorgetroya80.madtransit.modules.bus.ports

import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot

/** Source of the next arrivals at one EMT bus stop, by its number. */
interface ArrivalProvider {
    fun arrivals(number: String): ArrivalSnapshot
}
