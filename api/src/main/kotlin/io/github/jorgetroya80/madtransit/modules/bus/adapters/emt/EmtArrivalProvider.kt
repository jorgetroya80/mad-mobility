package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.ArrivalProvider
import io.github.jorgetroya80.madtransit.shared.emt.CacheService
import io.github.jorgetroya80.madtransit.shared.emt.EmtHttpClient
import io.github.jorgetroya80.madtransit.shared.emt.EmtProtocolError
import io.github.jorgetroya80.madtransit.shared.emt.EmtResponse
import org.springframework.stereotype.Component

/** The next arrivals at one stop from EMT MobilityLabs, cached per stop. Serves the last good ones as stale when the EMT fails. */
@Component
class EmtArrivalProvider(
    private val emtHttpClient: EmtHttpClient,
    private val cacheService: CacheService,
) : ArrivalProvider {
    override fun arrivals(number: String): ArrivalSnapshot {
        val cached = cacheService.get(MODULE, number) { load(number) }
        return when (val result = cached.value) {
            is StopArrivals.Available -> ArrivalSnapshot(result.arrivals, cached.updatedAt, cached.stale)
            is StopArrivals.Unavailable -> throw EmtProtocolError(EmtResponse.CODE_TOKEN_INVALID, result.reason)
        }
    }

    // A stop the EMT does not serve answers code 80 even after a relogin. Caching that answer for the
    // TTL spares the EMT call and the relogin, which invalidates the shared token, on every poll
    private fun load(number: String): StopArrivals =
        try {
            StopArrivals.Available(fetch(number))
        } catch (e: EmtProtocolError) {
            if (e.code != EmtResponse.CODE_TOKEN_INVALID) throw e
            StopArrivals.Unavailable(e.message.orEmpty())
        }

    // No arrivals is a valid answer (no bus coming), unlike an empty stop list
    private fun fetch(number: String): List<Arrival> {
        val data = emtHttpClient.post(MODULE, "$PATH_PREFIX$number$PATH_SUFFIX", EmtArrivesData::class.java, ARRIVES_REQUEST)
        return EmtArrivalMapper.toDomain(number, data.firstOrNull()?.arrive.orEmpty())
    }

    private sealed interface StopArrivals {
        data class Available(
            val arrivals: List<Arrival>,
        ) : StopArrivals

        data class Unavailable(
            val reason: String,
        ) : StopArrivals
    }

    private companion object {
        const val MODULE = "bus-arrivals"
        const val PATH_PREFIX = "/v2/transport/busemtmad/stops/"
        const val PATH_SUFFIX = "/arrives/"

        // Estimates only, in Spanish: stop info and incidents are not used
        val ARRIVES_REQUEST =
            mapOf(
                "cultureInfo" to "ES",
                "Text_StopRequired_YN" to "N",
                "Text_EstimationsRequired_YN" to "Y",
                "Text_IncidencesRequired_YN" to "N",
            )
    }
}
