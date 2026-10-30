package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusLine
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetail
import org.slf4j.LoggerFactory
import java.time.DayOfWeek

/** Anticorruption layer: translates the EMT detail of a stop to the domain, with the schedule of [DayOfWeek] today. */
object EmtBusStopDetailMapper {
    private val log = LoggerFactory.getLogger(EmtBusStopDetailMapper::class.java)

    private const val WORKDAY = "LA"
    private const val FRIDAY_OR_HOLIDAY_EVE = "VI"
    private const val SATURDAY = "SA"
    private const val SUNDAY_OR_HOLIDAY = "FE"

    fun toDomain(
        stop: EmtBusStopDetail,
        today: DayOfWeek,
    ): BusStopDetail =
        BusStopDetail(
            address = stop.postalAddress?.trim()?.takeIf { it.isNotEmpty() },
            lines =
                stop.dataLine
                    .orEmpty()
                    .groupBy { it.line ?: it.label }
                    .values
                    .mapNotNull { toBusLineOrNull(stop.stop, it, today) },
        )

    // The EMT usually sends one entry per line, already today's; several only matter when it sends more
    private fun todaysEntry(
        entries: List<EmtStopDetailLine>,
        today: DayOfWeek,
    ): EmtStopDetailLine? =
        entries.singleOrNull()
            ?: dayTypesOn(today).firstNotNullOfOrNull { dayType -> entries.find { it.dayType == dayType } }

    private fun dayTypesOn(day: DayOfWeek): List<String> =
        when (day) {
            DayOfWeek.FRIDAY -> listOf(FRIDAY_OR_HOLIDAY_EVE, WORKDAY)
            DayOfWeek.SATURDAY -> listOf(SATURDAY)
            DayOfWeek.SUNDAY -> listOf(SUNDAY_OR_HOLIDAY)
            else -> listOf(WORKDAY)
        }

    private fun toBusLineOrNull(
        number: String?,
        entries: List<EmtStopDetailLine>,
        today: DayOfWeek,
    ): BusLine? {
        val schedule = todaysEntry(entries, today)
        val line = schedule ?: entries.first()
        val label = line.label ?: return discard(number, line)
        return BusLine(
            label = label,
            headerA = line.headerA ?: return discard(number, line),
            headerB = line.headerB ?: return discard(number, line),
            direction = line.direction ?: return discard(number, line),
            firstBus = schedule?.startTime,
            lastBus = schedule?.stopTime,
            minFrequencyMinutes = schedule?.minFreq?.let { minutesOrNull(it, number, label) },
            maxFrequencyMinutes = schedule?.maxFreq?.let { minutesOrNull(it, number, label) },
        )
    }

    private fun minutesOrNull(
        frequency: String,
        number: String?,
        label: String,
    ): Int? {
        val minutes = frequency.trim().toIntOrNull()
        if (minutes == null) log.warn("Unreadable EMT bus frequency {} at stop={} line={}: sent as null", frequency, number, label)
        return minutes
    }

    private fun discard(
        number: String?,
        line: EmtStopDetailLine,
    ): BusLine? {
        log.warn("Discarding EMT bus line {} at stop={}: missing label, headers or direction", line.line, number)
        return null
    }
}
