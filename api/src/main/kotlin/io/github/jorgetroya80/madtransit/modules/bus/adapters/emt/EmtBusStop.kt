package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint

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

private const val LON_INDEX = 0
private const val LAT_INDEX = 1
private const val COORDINATES_SIZE = 2

/** The point, or null when the coordinates are missing, are not exactly `[lon, lat]` or are out of range. */
internal fun EmtGeometry.toGeoPointOrNull(): GeoPoint? {
    val lonLat = coordinates?.takeIf { it.size == COORDINATES_SIZE } ?: return null
    return try {
        GeoPoint(lonLat[LAT_INDEX], lonLat[LON_INDEX])
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** A line as returned by EMT `GET /v1/transport/busemtmad/lines/info/{yyyyMMdd}/`: internal code and public label. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtLineInfo(
    val line: String?,
    val label: String?,
)
