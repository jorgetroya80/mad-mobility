package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import org.slf4j.LoggerFactory

/** Anticorruption layer: translates the EMT arrivals at a stop to the domain, in EMT order. */
object EmtArrivalMapper {
    private val log = LoggerFactory.getLogger(EmtArrivalMapper::class.java)

    // EMT sends 999999 when it has no estimate for the bus
    private const val NO_ESTIMATE_SECONDS = 999_999
    private const val SECONDS_PER_MINUTE = 60

    // EMT sends [0, 0] for a bus it cannot locate
    private val UNKNOWN_POSITION = GeoPoint(0.0, 0.0)

    fun toDomain(
        stop: String,
        arrives: List<EmtArrive>,
    ): List<Arrival> = arrives.mapNotNull { toArrivalOrNull(stop, it) }

    private fun toArrivalOrNull(
        stop: String,
        arrive: EmtArrive,
    ): Arrival? {
        val line = arrive.line ?: return discard(stop, arrive)
        val destination = arrive.destination ?: return discard(stop, arrive)
        val seconds = arrive.estimateArrive?.takeIf { it < NO_ESTIMATE_SECONDS }
        if (seconds == null) return Arrival(line, destination, minutes = null, noEstimate = true, distanceMeters = null, bus = null)
        return Arrival(
            line = line,
            destination = destination,
            minutes = seconds / SECONDS_PER_MINUTE,
            noEstimate = false,
            distanceMeters = arrive.distanceBus,
            bus = arrive.geometry?.toGeoPointOrNull()?.takeUnless { it == UNKNOWN_POSITION },
        )
    }

    private fun discard(
        stop: String,
        arrive: EmtArrive,
    ): Arrival? {
        log.warn(
            "Discarding EMT bus arrival line={} destination={} at stop={}: missing line or destination",
            arrive.line,
            arrive.destination,
            stop,
        )
        return null
    }
}
