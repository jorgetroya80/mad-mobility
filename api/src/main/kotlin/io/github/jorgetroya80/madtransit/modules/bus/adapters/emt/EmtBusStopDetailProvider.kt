package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetailSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopDetailProvider
import io.github.jorgetroya80.madtransit.shared.emt.CacheService
import io.github.jorgetroya80.madtransit.shared.emt.EmtConfig
import io.github.jorgetroya80.madtransit.shared.emt.EmtHttpClient
import io.github.jorgetroya80.madtransit.shared.emt.EmtProtocolError
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/** The detail of one stop from EMT MobilityLabs, cached per stop. Serves the last good one as stale when the EMT fails. */
@Component
class EmtBusStopDetailProvider(
    private val emtHttpClient: EmtHttpClient,
    private val cacheService: CacheService,
    private val clock: Clock,
) : BusStopDetailProvider {
    // The EMT answer is cached as is and mapped per request, so a detail cached yesterday gets today's schedule
    override fun detail(number: String): BusStopDetailSnapshot {
        val cached = cacheService.get(MODULE, number) { load(number) }
        val today = LocalDate.ofInstant(clock.instant(), EmtConfig.MADRID).dayOfWeek
        return BusStopDetailSnapshot(EmtBusStopDetailMapper.toDomain(cached.value, today), cached.updatedAt, cached.stale)
    }

    private fun load(number: String): EmtBusStopDetail =
        emtHttpClient
            .get(MODULE, "$PATH_PREFIX$number$PATH_SUFFIX", EmtBusStopDetailData::class.java)
            .firstOrNull()
            ?.stops
            ?.firstOrNull()
            ?: throw EmtProtocolError(null, "EMT returned no detail for bus stop $number")

    private companion object {
        const val MODULE = "bus-stop-detail"
        const val PATH_PREFIX = "/v1/transport/busemtmad/stops/"
        const val PATH_SUFFIX = "/detail/"
    }
}
