package io.github.jorgetroya80.madmobility.modules.bicimad.application

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import java.time.Instant

/** The station with the given id from the current snapshot; deleted stations are not in it, so they are not found. */
@Service
class GetStation(
    private val provider: StationProvider,
) {
    operator fun invoke(id: Int): FoundStation {
        val snapshot = provider.snapshot()
        val station = snapshot.stations.find { it.id == id } ?: throw StationNotFound(id)
        return FoundStation(station, snapshot.updatedAt, snapshot.stale)
    }
}

data class FoundStation(
    val station: Station,
    val updatedAt: Instant,
    val stale: Boolean,
)

@ResponseStatus(HttpStatus.NOT_FOUND)
class StationNotFound(
    id: Int,
) : RuntimeException("BiciMAD station $id not found")
