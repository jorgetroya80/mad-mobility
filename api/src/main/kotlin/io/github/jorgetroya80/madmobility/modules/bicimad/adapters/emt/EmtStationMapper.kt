package io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import org.slf4j.LoggerFactory

/** Anticorruption layer: translates EMT stations to the domain, discarding deleted or broken ones. */
object EmtStationMapper {
    private val log = LoggerFactory.getLogger(EmtStationMapper::class.java)

    private const val ACTIVATED = 1
    private const val NOT_UNAVAILABLE = 0
    private const val NAME_SEPARATOR = " - "
    private const val LON_INDEX = 0
    private const val LAT_INDEX = 1
    private const val COORDINATES_SIZE = 2

    // EMT light codes are not ordered by occupancy
    private const val LIGHT_LOW = 0
    private const val LIGHT_HIGH = 1
    private const val LIGHT_MEDIUM = 2
    private const val LIGHT_UNKNOWN = 3
    private val OCCUPANCY_BY_LIGHT =
        mapOf(
            LIGHT_LOW to Occupancy.LOW,
            LIGHT_MEDIUM to Occupancy.MEDIUM,
            LIGHT_HIGH to Occupancy.HIGH,
            LIGHT_UNKNOWN to Occupancy.UNKNOWN,
        )

    fun toDomain(stations: List<EmtStation>): List<Station> =
        stations.filterNot { it.virtualDelete == true }.mapNotNull { toStationOrNull(it) ?: discard(it) }

    private fun toStationOrNull(emt: EmtStation): Station? {
        val coordinates = emt.geometry?.coordinates?.takeIf { it.size == COORDINATES_SIZE } ?: return null
        val number = emt.number ?: return null
        return Station(
            id = emt.id ?: return null,
            number = number,
            name = emt.name?.removePrefix("$number$NAME_SEPARATOR") ?: return null,
            address = emt.address ?: return null,
            lat = coordinates[LAT_INDEX],
            lon = coordinates[LON_INDEX],
            bikes = emt.dockBikes ?: return null,
            freeDocks = emt.freeBases ?: return null,
            totalDocks = emt.totalBases ?: return null,
            status = statusOf(emt),
            occupancy = occupancyOf(emt),
        )
    }

    private fun discard(emt: EmtStation): Station? {
        log.warn("Discarding EMT BiciMAD station id={}: missing required field or coordinates", emt.id)
        return null
    }

    private fun statusOf(emt: EmtStation): StationStatus =
        if (emt.activate == ACTIVATED && emt.noAvailable == NOT_UNAVAILABLE) StationStatus.OPERATIONAL else StationStatus.NO_SERVICE

    private fun occupancyOf(emt: EmtStation): Occupancy =
        OCCUPANCY_BY_LIGHT[emt.light]
            ?: Occupancy.UNKNOWN.also { log.warn("Unknown EMT light {} for BiciMAD station id={}", emt.light, emt.id) }
}
