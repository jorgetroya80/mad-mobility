package io.github.jorgetroya80.madmobility.modules.bicimad.application

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import org.springframework.stereotype.Service

/** Every station, ordered by number. */
@Service
class ListStations(
    private val provider: StationProvider,
) {
    operator fun invoke(): StationSnapshot {
        val snapshot = provider.snapshot()
        return snapshot.copy(stations = snapshot.stations.sortedWith(Station.BY_NUMBER))
    }
}
