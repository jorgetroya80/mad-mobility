package io.github.jorgetroya80.madmobility.modules.bicimad.http

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import java.time.Instant

data class StationsResponse(
    val stations: List<StationResponse>,
    val updatedAt: Instant,
    val stale: Boolean,
    val source: String = SOURCE,
) {
    companion object {
        const val SOURCE = "EMT Madrid MobilityLabs"

        fun from(snapshot: StationSnapshot) =
            StationsResponse(snapshot.stations.map(StationResponse::from), snapshot.updatedAt, snapshot.stale)
    }
}

data class StationResponse(
    val id: Int,
    val number: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val status: StationStatus,
    val bikes: Int,
    val freeDocks: Int,
    val totalDocks: Int,
    val occupancy: Occupancy,
) {
    companion object {
        fun from(station: Station) =
            StationResponse(
                id = station.id,
                number = station.number,
                name = station.name,
                address = station.address,
                lat = station.lat,
                lon = station.lon,
                status = station.status,
                bikes = station.bikes,
                freeDocks = station.freeDocks,
                totalDocks = station.totalDocks,
                occupancy = station.occupancy,
            )
    }
}
