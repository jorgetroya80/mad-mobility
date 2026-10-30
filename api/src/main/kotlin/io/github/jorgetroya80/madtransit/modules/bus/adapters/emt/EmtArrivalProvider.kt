package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.ArrivalProvider
import io.github.jorgetroya80.madtransit.shared.emt.CacheService
import io.github.jorgetroya80.madtransit.shared.emt.EmtHttpClient
import org.springframework.stereotype.Component

/** The next arrivals at one stop from EMT MobilityLabs, cached per stop. Serves the last good ones as stale when the EMT fails. */
@Component
class EmtArrivalProvider(
    private val emtHttpClient: EmtHttpClient,
    private val cacheService: CacheService,
) : ArrivalProvider {
    override fun arrivals(number: String): ArrivalSnapshot {
        val cached = cacheService.get(MODULE, number) { load(number) }
        return ArrivalSnapshot(cached.value, cached.updatedAt, cached.stale)
    }

    // No arrivals is a valid answer (no bus coming), unlike an empty stop list
    private fun load(number: String): List<Arrival> {
        val data = emtHttpClient.post(MODULE, "$PATH_PREFIX$number$PATH_SUFFIX", EmtArrivesData::class.java, ARRIVES_REQUEST)
        return EmtArrivalMapper.toDomain(number, data.firstOrNull()?.arrive.orEmpty())
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
