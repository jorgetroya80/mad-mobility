package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * A stop as returned by EMT `POST /v1/transport/busemtmad/stops/list/`. Every field is nullable so one
 * broken stop can be discarded by [EmtBusStopMapper] without failing the whole response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtBusStop(
    val node: String?,
    val name: String?,
    val geometry: EmtGeometry?,
    /** Internal codes `"<line>/<direction>"` (`"501/2"`), translated by [EmtLineInfo]. */
    val lines: List<String>?,
)

/** GeoJSON point: `coordinates` is `[lon, lat]`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtGeometry(
    val coordinates: List<Double>?,
)

/** A line as returned by EMT `GET /v1/transport/busemtmad/lines/info/{yyyyMMdd}/`: internal code and public label. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtLineInfo(
    val line: String?,
    val label: String?,
)
