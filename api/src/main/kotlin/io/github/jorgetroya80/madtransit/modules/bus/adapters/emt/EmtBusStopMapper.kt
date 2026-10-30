package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import org.slf4j.LoggerFactory

/** Anticorruption layer: translates EMT stops to the domain, with public line labels from the line catalog. */
object EmtBusStopMapper {
    private val log = LoggerFactory.getLogger(EmtBusStopMapper::class.java)

    private const val DIRECTION_SEPARATOR = '/'
    private const val LON_INDEX = 0
    private const val LAT_INDEX = 1
    private const val COORDINATES_SIZE = 2

    fun toDomain(
        stops: List<EmtBusStop>,
        lines: List<EmtLineInfo>,
    ): List<BusStop> {
        val labelsByCode = labelsByCode(lines)
        return stops.mapNotNull { toBusStopOrNull(it, labelsByCode) ?: discard(it) }
    }

    private fun labelsByCode(lines: List<EmtLineInfo>): Map<String, String> =
        lines
            .mapNotNull { info -> info.line?.let { code -> info.label?.let { label -> code to label } } }
            .toMap()

    private fun toBusStopOrNull(
        emt: EmtBusStop,
        labelsByCode: Map<String, String>,
    ): BusStop? {
        val number = emt.node?.takeIf { it.isNotBlank() } ?: return null
        return BusStop(
            number = number,
            name = emt.name ?: return null,
            location = emt.geometry?.coordinates?.let(::locationOrNull) ?: return null,
            lines = labelsOf(number, emt.lines.orEmpty(), labelsByCode),
        )
    }

    private fun labelsOf(
        number: String,
        codes: List<String>,
        labelsByCode: Map<String, String>,
    ): List<String> =
        codes
            .map { it.substringBefore(DIRECTION_SEPARATOR) }
            .distinct()
            .map { code -> labelsByCode[code] ?: unknownLine(number, code) }

    private fun locationOrNull(coordinates: List<Double>): GeoPoint? {
        if (coordinates.size != COORDINATES_SIZE) return null
        return try {
            GeoPoint(coordinates[LAT_INDEX], coordinates[LON_INDEX])
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun unknownLine(
        number: String,
        code: String,
    ): String {
        log.warn("Unknown EMT bus line code {} at stop={}: kept as is", code, number)
        return code
    }

    private fun discard(emt: EmtBusStop): BusStop? {
        log.warn("Discarding EMT bus stop node={} name={}: missing number, name or valid coordinates", emt.node, emt.name)
        return null
    }
}
