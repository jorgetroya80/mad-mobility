package io.github.jorgetroya80.madtransit.modules.bus.domain

/** An EMT bus stop in the API's own terms: [lines] are public labels (`N1`), not EMT internal codes. */
data class BusStop(
    val number: String,
    val name: String,
    val location: GeoPoint,
    val lines: List<String>,
)
