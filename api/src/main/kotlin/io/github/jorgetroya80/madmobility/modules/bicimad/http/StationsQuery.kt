package io.github.jorgetroya80.madmobility.modules.bicimad.http

import io.github.jorgetroya80.madmobility.modules.bicimad.application.SearchArea
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.GeoPoint
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Need
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Query parameters of `GET /v1/bicimad/stations`; any invalid one is a 400 problem. */
data class StationsQuery(
    @Parameter(description = "Search centre as `lat,lon` (WGS84).", example = "40.4168,-3.7038")
    val near: String?,
    @Parameter(
        description = "Search radius in meters around `near`; only valid with `near`.",
        schema = Schema(type = "integer", format = "int32", minimum = "1", maximum = "5000", defaultValue = "500"),
    )
    val radius: Int?,
    @Parameter(
        description = "Put first the stations with a bike to take or a free dock to leave one.",
        schema = Schema(allowableValues = ["bikes", "docks"]),
    )
    val need: String?,
) {
    fun area(): SearchArea? {
        if (near == null) {
            if (radius != null) throw badRequest("radius is only valid with near")
            return null
        }
        val radiusMeters = radius ?: DEFAULT_RADIUS_METERS
        if (radiusMeters !in RADIUS_RANGE_METERS) throw badRequest("radius must be in $RADIUS_RANGE_METERS meters")
        return SearchArea(parseNear(near), radiusMeters.toDouble())
    }

    fun need(): Need? =
        need?.let { value -> Need.entries.find { it.name.lowercase() == value } ?: throw badRequest("need must be bikes or docks") }

    private fun parseNear(value: String): GeoPoint {
        val coordinates = value.split(NEAR_SEPARATOR).map(String::toDoubleOrNull)
        val lat = coordinates.first()
        val lon = coordinates.getOrNull(LON_INDEX)
        if (coordinates.size != NEAR_SIZE || lat == null || lon == null) throw badRequest("near must be lat,lon")
        try {
            return GeoPoint(lat, lon)
        } catch (e: IllegalArgumentException) {
            throw badRequest("near ${e.message}")
        }
    }

    private fun badRequest(detail: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, detail)

    private companion object {
        const val DEFAULT_RADIUS_METERS = 500
        const val MIN_RADIUS_METERS = 1
        const val MAX_RADIUS_METERS = 5000
        val RADIUS_RANGE_METERS = MIN_RADIUS_METERS..MAX_RADIUS_METERS
        const val NEAR_SEPARATOR = ","
        const val NEAR_SIZE = 2
        const val LON_INDEX = 1
    }
}
