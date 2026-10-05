package io.github.jorgetroya80.madmobility.modules.bicimad.application

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.GeoPoint
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Need
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Stations inside [SearchArea] (all of them without one), available ones for the [Need] first,
 * then by distance with an area or by number without one. [Need] only orders, never filters.
 */
@Service
class FindNearbyStations(
    private val provider: StationProvider,
) {
    operator fun invoke(
        area: SearchArea? = null,
        need: Need? = null,
    ): NearbyStations {
        val snapshot = provider.snapshot()
        val stations = locate(snapshot.stations, area).sortedWith(orderFor(area, need))
        return NearbyStations(stations, snapshot.updatedAt, snapshot.stale)
    }

    private fun locate(
        stations: List<Station>,
        area: SearchArea?,
    ): List<NearbyStation> {
        if (area == null) return stations.map { NearbyStation(it, distanceMeters = null) }
        return stations.mapNotNull { station ->
            val distance = area.center.distanceTo(station.location)
            if (distance <= area.radiusMeters) NearbyStation(station, distance) else null
        }
    }

    private fun orderFor(
        area: SearchArea?,
        need: Need?,
    ): Comparator<NearbyStation> {
        val withinGroup: Comparator<NearbyStation> =
            if (area == null) compareBy(Station.BY_NUMBER) { it.station } else compareBy { it.distanceMeters }
        if (need == null) return withinGroup
        return compareByDescending<NearbyStation> { it.station.isAvailableFor(need) }.then(withinGroup)
    }
}

data class SearchArea(
    val center: GeoPoint,
    val radiusMeters: Double,
)

/** [distanceMeters] is only known when searching inside an area. */
data class NearbyStation(
    val station: Station,
    val distanceMeters: Double?,
)

data class NearbyStations(
    val stations: List<NearbyStation>,
    val updatedAt: Instant,
    val stale: Boolean,
)
