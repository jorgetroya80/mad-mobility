package io.github.jorgetroya80.madmobility.modules.bicimad.http

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.jorgetroya80.madmobility.modules.bicimad.application.NearbyStation
import io.github.jorgetroya80.madmobility.modules.bicimad.application.NearbyStations
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import java.time.Instant
import kotlin.math.roundToInt

data class StationsResponse(
    val stations: List<StationResponse>,
    val updatedAt: Instant,
    val stale: Boolean,
    val source: String = SOURCE,
) {
    companion object {
        const val SOURCE = "EMT Madrid MobilityLabs"

        fun from(result: NearbyStations) = StationsResponse(result.stations.map(StationResponse::from), result.updatedAt, result.stale)
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
    @JsonInclude(JsonInclude.Include.NON_NULL) val distanceMeters: Int?,
) {
    companion object {
        fun from(nearby: NearbyStation): StationResponse {
            val station = nearby.station
            return StationResponse(
                id = station.id,
                number = station.number,
                name = station.name,
                address = station.address,
                lat = station.location.lat,
                lon = station.location.lon,
                status = station.status,
                bikes = station.bikes,
                freeDocks = station.freeDocks,
                totalDocks = station.totalDocks,
                occupancy = station.occupancy,
                distanceMeters = nearby.distanceMeters?.roundToInt(),
            )
        }
    }
}
