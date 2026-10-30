package io.github.jorgetroya80.madtransit.modules.bus.http

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.temporal.ChronoUnit

@Schema(requiredProperties = ["arrivals", "updatedAt", "stale", "source"])
data class BusStopArrivalsResponse(
    @Schema(description = "Soonest first and those without an estimate last; ties by line.")
    val arrivals: List<BusArrivalResponse>,
    val updatedAt: Instant,
    val stale: Boolean,
    val source: String = BusStopsResponse.SOURCE,
) {
    companion object {
        fun from(snapshot: ArrivalSnapshot) =
            BusStopArrivalsResponse(
                snapshot.arrivals.map(BusArrivalResponse::from),
                snapshot.updatedAt.truncatedTo(ChronoUnit.SECONDS),
                snapshot.stale,
            )
    }
}

/** A coming bus. Without an estimate (`noEstimate`), minutes, distance and position are null. */
@Schema(requiredProperties = ["line", "destination", "minutes", "noEstimate", "distanceMeters", "busLat", "busLon"])
data class BusArrivalResponse(
    @Schema(description = "Public label (`N1`).")
    val line: String,
    val destination: String,
    @Schema(types = ["integer", "null"], format = "int32", description = "Whole minutes to arrival, rounded down; `0` is arriving.")
    val minutes: Int?,
    @Schema(description = "The EMT has no estimate for this bus.")
    val noEstimate: Boolean,
    @Schema(types = ["integer", "null"], format = "int32", description = "Meters from the bus to the stop, as the EMT reports it.")
    val distanceMeters: Int?,
    @Schema(types = ["number", "null"], format = "double", description = "Bus position (WGS84); null when the EMT does not know it.")
    val busLat: Double?,
    @Schema(types = ["number", "null"], format = "double", description = "Bus position (WGS84); null when the EMT does not know it.")
    val busLon: Double?,
) {
    companion object {
        fun from(arrival: Arrival) =
            BusArrivalResponse(
                line = arrival.line,
                destination = arrival.destination,
                minutes = arrival.minutes,
                noEstimate = arrival.noEstimate,
                distanceMeters = arrival.distanceMeters,
                busLat = arrival.bus?.lat,
                busLon = arrival.bus?.lon,
            )
    }
}
