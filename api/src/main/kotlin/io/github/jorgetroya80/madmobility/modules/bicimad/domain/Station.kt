package io.github.jorgetroya80.madmobility.modules.bicimad.domain

/** A BiciMAD station in the API's own terms, independent of the EMT format. */
data class Station(
    val id: Int,
    val number: String,
    val name: String,
    val address: String,
    val location: GeoPoint,
    val bikes: Int,
    val freeDocks: Int,
    val totalDocks: Int,
    val status: StationStatus,
    val occupancy: Occupancy,
) {
    fun isAvailableFor(need: Need): Boolean =
        status == StationStatus.OPERATIONAL &&
            when (need) {
                Need.BIKES -> bikes > 0
                Need.DOCKS -> freeDocks > 0
            }

    companion object {
        /** By the numeric part of [number] and then its suffix: 5, 5a, 5b, 10. Numbers without digits go last. */
        val BY_NUMBER: Comparator<Station> =
            compareBy<Station> { it.number.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.number.dropWhile(Char::isDigit) }
    }
}

enum class StationStatus { OPERATIONAL, NO_SERVICE }

enum class Occupancy { LOW, MEDIUM, HIGH, UNKNOWN }

/** What the user is looking for: a bike to take or a free dock to leave one. */
enum class Need { BIKES, DOCKS }
