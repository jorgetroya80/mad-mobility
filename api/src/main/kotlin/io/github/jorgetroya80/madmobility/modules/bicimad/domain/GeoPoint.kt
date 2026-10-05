package io.github.jorgetroya80.madmobility.modules.bicimad.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS 84 position; distances use the haversine formula on a spherical Earth. */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
) {
    init {
        require(lat in MIN_LAT..MAX_LAT) { "lat out of range: $lat" }
        require(lon in MIN_LON..MAX_LON) { "lon out of range: $lon" }
    }

    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    private companion object {
        const val MIN_LAT = -90.0
        const val MAX_LAT = 90.0
        const val MIN_LON = -180.0
        const val MAX_LON = 180.0
        const val EARTH_RADIUS_METERS = 6_371_008.8
    }
}
