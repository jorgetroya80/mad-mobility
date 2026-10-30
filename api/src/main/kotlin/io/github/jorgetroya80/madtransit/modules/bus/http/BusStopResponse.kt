package io.github.jorgetroya80.madtransit.modules.bus.http

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.jorgetroya80.madtransit.modules.bus.application.NearbyStop
import io.github.jorgetroya80.madtransit.modules.bus.application.NearbyStops
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Schema(requiredProperties = ["stops", "updatedAt", "stale", "source"])
data class BusStopsResponse(
    val stops: List<BusStopResponse>,
    val updatedAt: Instant,
    val stale: Boolean,
    val source: String = SOURCE,
) {
    companion object {
        const val SOURCE = "EMT Madrid MobilityLabs"

        fun from(result: NearbyStops) =
            BusStopsResponse(result.stops.map(BusStopResponse::from), result.updatedAt.truncatedTo(ChronoUnit.SECONDS), result.stale)
    }
}

@Schema(requiredProperties = ["number", "name", "lat", "lon", "lines"])
data class BusStopResponse(
    val number: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    @Schema(description = "Public labels of the lines that stop here (`N1`), in EMT order.")
    val lines: List<String>,
    @Schema(description = "Meters from `near`; only when `near` is given.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val distanceMeters: Int?,
) {
    companion object {
        fun from(nearby: NearbyStop) =
            BusStopResponse(
                number = nearby.stop.number,
                name = nearby.stop.name,
                lat = nearby.stop.location.lat,
                lon = nearby.stop.location.lon,
                lines = nearby.stop.lines,
                distanceMeters = nearby.distanceMeters?.roundToInt(),
            )
    }
}
