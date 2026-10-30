package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopIndex
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import io.github.jorgetroya80.madtransit.shared.emt.CacheService
import io.github.jorgetroya80.madtransit.shared.emt.EmtConfig
import io.github.jorgetroya80.madtransit.shared.emt.EmtHttpClient
import io.github.jorgetroya80.madtransit.shared.emt.EmtProtocolError
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bus stops from EMT MobilityLabs: the stop list, translated with the line catalog, and the catalog
 * itself, each cached on its own. Serves the last good ones as stale when the EMT fails.
 */
@Component
class EmtBusStopProvider(
    private val emtHttpClient: EmtHttpClient,
    private val cacheService: CacheService,
    private val clock: Clock,
) : BusStopProvider {
    // Stops are cached translated and indexed, so the 4,900 stops are mapped once per refresh, not per request
    override fun snapshot(): BusStopSnapshot {
        val lines = cacheService.get(LINES_MODULE, CACHE_KEY, ::loadLines)
        val stops = cacheService.get(STOPS_MODULE, CACHE_KEY) { loadStops(lines.value) }
        return BusStopSnapshot(
            index = stops.value,
            updatedAt = minOf(stops.updatedAt, lines.updatedAt),
            stale = stops.stale || lines.stale,
        )
    }

    // Both loaders reject empty responses so the cache keeps serving the last good ones
    private fun loadLines(): List<EmtLineInfo> {
        val lines = emtHttpClient.get(LINES_MODULE, linesPath(), EmtLineInfo::class.java)
        if (lines.isEmpty()) throw EmtProtocolError(null, "EMT returned an empty bus line catalog")
        return lines
    }

    private fun loadStops(lines: List<EmtLineInfo>): BusStopIndex {
        val stops = EmtBusStopMapper.toDomain(emtHttpClient.post(STOPS_MODULE, STOPS_PATH, EmtBusStop::class.java), lines)
        if (stops.isEmpty()) throw EmtProtocolError(null, "EMT returned no valid bus stops")
        return BusStopIndex(stops)
    }

    private fun linesPath(): String {
        val today = LocalDate.ofInstant(clock.instant(), EmtConfig.MADRID)
        return "$LINES_PATH_PREFIX${today.format(DateTimeFormatter.BASIC_ISO_DATE)}/"
    }

    private companion object {
        const val STOPS_MODULE = "bus-stops"
        const val LINES_MODULE = "bus-lines"
        const val CACHE_KEY = "all"
        const val STOPS_PATH = "/v1/transport/busemtmad/stops/list/"
        const val LINES_PATH_PREFIX = "/v1/transport/busemtmad/lines/info/"
    }
}
