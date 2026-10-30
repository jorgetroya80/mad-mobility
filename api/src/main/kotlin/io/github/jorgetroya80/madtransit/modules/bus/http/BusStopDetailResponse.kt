package io.github.jorgetroya80.madtransit.modules.bus.http

import io.github.jorgetroya80.madtransit.modules.bus.application.FoundStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusLine
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.temporal.ChronoUnit

@Schema(requiredProperties = ["stop", "updatedAt", "stale", "source"])
data class BusStopDetailResponse(
    val stop: BusStopInfoResponse,
    val updatedAt: Instant,
    val stale: Boolean,
    val source: String = BusStopsResponse.SOURCE,
) {
    companion object {
        fun from(result: FoundStop) =
            BusStopDetailResponse(BusStopInfoResponse.from(result), result.updatedAt.truncatedTo(ChronoUnit.SECONDS), result.stale)
    }
}

/** A stop with its address and its lines; unlike in the list, lines carry headers and today's schedule. */
@Schema(requiredProperties = ["number", "name", "address", "lat", "lon", "lines"])
data class BusStopInfoResponse(
    val number: String,
    val name: String,
    @Schema(types = ["string", "null"], description = "Postal address; null when the EMT has none.")
    val address: String?,
    val lat: Double,
    val lon: Double,
    @Schema(description = "Lines that stop here, in EMT order.")
    val lines: List<BusLineResponse>,
) {
    companion object {
        fun from(result: FoundStop) =
            BusStopInfoResponse(
                number = result.stop.number,
                name = result.stop.name,
                address = result.detail.address,
                lat = result.stop.location.lat,
                lon = result.stop.location.lon,
                lines = result.detail.lines.map(BusLineResponse::from),
            )
    }
}

/** Schedule and frequencies are today's in Madrid, and null when the EMT has none for today. */
@Schema(
    requiredProperties = [
        "label", "headerA", "headerB", "direction", "firstBus", "lastBus", "minFrequencyMinutes", "maxFrequencyMinutes",
    ],
)
data class BusLineResponse(
    @Schema(description = "Public label (`N1`).")
    val label: String,
    val headerA: String,
    val headerB: String,
    @Schema(description = "`A` goes towards `headerA`, `B` towards `headerB`.")
    val direction: String,
    @Schema(types = ["string", "null"], description = "First bus, `HH:mm`. Night lines cross midnight: later than `lastBus`.")
    val firstBus: String?,
    @Schema(types = ["string", "null"], description = "Last bus, `HH:mm`.")
    val lastBus: String?,
    @Schema(types = ["integer", "null"], format = "int32")
    val minFrequencyMinutes: Int?,
    @Schema(types = ["integer", "null"], format = "int32")
    val maxFrequencyMinutes: Int?,
) {
    companion object {
        fun from(line: BusLine) =
            BusLineResponse(
                label = line.label,
                headerA = line.headerA,
                headerB = line.headerB,
                direction = line.direction,
                firstBus = line.firstBus,
                lastBus = line.lastBus,
                minFrequencyMinutes = line.minFrequencyMinutes,
                maxFrequencyMinutes = line.maxFrequencyMinutes,
            )
    }
}
