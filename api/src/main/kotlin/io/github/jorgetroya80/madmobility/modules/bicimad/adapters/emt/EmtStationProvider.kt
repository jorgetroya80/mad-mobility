package io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import io.github.jorgetroya80.madmobility.shared.emt.CacheService
import io.github.jorgetroya80.madmobility.shared.emt.EmtHttpClient
import io.github.jorgetroya80.madmobility.shared.emt.EmtProtocolError
import org.springframework.stereotype.Component

/** Stations from EMT MobilityLabs, translated and cached; serves the last good ones as stale when the EMT fails. */
@Component
class EmtStationProvider(
    private val emtHttpClient: EmtHttpClient,
    private val cacheService: CacheService,
) : StationProvider {
    override fun snapshot(): StationSnapshot {
        val cached = cacheService.get(MODULE, CACHE_KEY, ::loadStations)
        return StationSnapshot(cached.value, cached.updatedAt, cached.stale)
    }

    // Inside the loader so a response without valid stations keeps serving the cached ones
    private fun loadStations(): List<Station> {
        val stations = EmtStationMapper.toDomain(emtHttpClient.get(MODULE, STATIONS_PATH, EmtStation::class.java))
        if (stations.isEmpty()) throw EmtProtocolError(null, "EMT returned no valid BiciMAD stations")
        return stations
    }

    private companion object {
        const val MODULE = "bicimad"
        const val CACHE_KEY = "stations"
        const val STATIONS_PATH = "/v1/transport/bicimad/stations/"
    }
}
