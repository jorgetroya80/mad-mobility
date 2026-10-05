package io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A station as returned by EMT `/v1/transport/bicimad/stations/`. Every field is nullable so one
 * broken station can be discarded by [EmtStationMapper] without failing the whole response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtStation(
    val id: Int?,
    val number: String?,
    val name: String?,
    val address: String?,
    val geometry: EmtGeometry?,
    @JsonProperty("dock_bikes") val dockBikes: Int?,
    @JsonProperty("free_bases") val freeBases: Int?,
    @JsonProperty("total_bases") val totalBases: Int?,
    val activate: Int?,
    @JsonProperty("no_available") val noAvailable: Int?,
    val light: Int?,
    val virtualDelete: Boolean?,
)

/** GeoJSON point: `coordinates` is `[lon, lat]`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmtGeometry(
    val coordinates: List<Double>?,
)
