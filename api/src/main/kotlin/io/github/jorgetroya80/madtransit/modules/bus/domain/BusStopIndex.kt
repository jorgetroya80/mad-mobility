package io.github.jorgetroya80.madtransit.modules.bus.domain

/** Every stop in list order, indexed once by number so a lookup does not scan the 4,900 stops. */
data class BusStopIndex(
    val stops: List<BusStop>,
) {
    private val byNumber: Map<String, BusStop> = stops.associateBy { it.number }

    fun find(number: String): BusStop? = byNumber[number]
}
