package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** An item of the `data` of EMT `POST /v2/transport/busemtmad/stops/{stopId}/arrives/`: one entry per coming bus. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtArrivesData(
    @param:JsonProperty("Arrive")
    val arrive: List<EmtArrive>?,
)

/**
 * A coming bus. [line] is already the public label; [estimateArrive] is in seconds and [distanceBus]
 * in meters. Every field is nullable so [EmtArrivalMapper] decides what is missing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtArrive(
    val line: String?,
    val destination: String?,
    val estimateArrive: Int?,
    @param:JsonProperty("DistanceBus")
    val distanceBus: Int?,
    val geometry: EmtGeometry?,
)
