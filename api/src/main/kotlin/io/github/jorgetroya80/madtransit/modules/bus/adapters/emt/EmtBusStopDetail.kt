package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** An item of the `data` of EMT `GET /v1/transport/busemtmad/stops/{stopId}/detail/`: the asked stop is in [stops]. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtBusStopDetailData(
    val stops: List<EmtBusStopDetail>?,
)

/** A stop in the EMT detail. Every field is nullable so [EmtBusStopDetailMapper] decides what is missing. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtBusStopDetail(
    val stop: String?,
    val name: String?,
    val postalAddress: String?,
    val geometry: EmtGeometry?,
    val dataLine: List<EmtStopDetailLine>?,
)

/** One line on one day type (`dayType`); times are `HH:mm` and frequencies are minutes as text (`"22"`). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtStopDetailLine(
    val line: String?,
    val label: String?,
    val direction: String?,
    val headerA: String?,
    val headerB: String?,
    val startTime: String?,
    val stopTime: String?,
    val minFreq: String?,
    val maxFreq: String?,
    val dayType: String?,
)
