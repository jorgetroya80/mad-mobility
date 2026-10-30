package io.github.jorgetroya80.madtransit.modules.bus.application

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import org.springframework.stereotype.Service
import java.time.Instant

/** Stops inside [SearchArea] by distance and then number, or every stop by number without one. */
@Service
class FindNearbyStops(
    private val provider: BusStopProvider,
) {
    operator fun invoke(area: SearchArea? = null): NearbyStops {
        val snapshot = provider.snapshot()
        val stops = locate(snapshot.stops, area).sortedWith(BY_DISTANCE_THEN_NUMBER)
        return NearbyStops(stops, snapshot.updatedAt, snapshot.stale)
    }

    private fun locate(
        stops: List<BusStop>,
        area: SearchArea?,
    ): List<NearbyStop> {
        if (area == null) return stops.map { NearbyStop(it, distanceMeters = null) }
        return stops.mapNotNull { stop ->
            val distance = area.center.distanceTo(stop.location)
            if (distance <= area.radiusMeters) NearbyStop(stop, distance) else null
        }
    }

    private companion object {
        // EMT stop numbers are numeric; without an area every distance is null and only the number decides
        val BY_DISTANCE_THEN_NUMBER: Comparator<NearbyStop> =
            compareBy<NearbyStop, Double?>(nullsFirst()) { it.distanceMeters }
                .thenBy { it.stop.number.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.stop.number }
    }
}

data class SearchArea(
    val center: GeoPoint,
    val radiusMeters: Double,
)

/** [distanceMeters] is only known when searching inside an area. */
data class NearbyStop(
    val stop: BusStop,
    val distanceMeters: Double?,
)

data class NearbyStops(
    val stops: List<NearbyStop>,
    val updatedAt: Instant,
    val stale: Boolean,
)
