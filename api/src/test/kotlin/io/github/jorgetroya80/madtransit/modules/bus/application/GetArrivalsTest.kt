package io.github.jorgetroya80.madtransit.modules.bus.application

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.ArrivalSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import io.github.jorgetroya80.madtransit.modules.bus.ports.ArrivalProvider
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class GetArrivalsTest {
    private val stops = mockk<BusStopProvider>()
    private val arrivals = mockk<ArrivalProvider>()
    private val getArrivals = GetArrivals(stops, arrivals)

    @Test
    fun `a number not in the list is not found and the arrivals are never asked for`() {
        givenStops("72")

        assertThatThrownBy { getArrivals("99999999") }.isInstanceOf(BusStopNotFound::class.java).hasMessageContaining("99999999")
        verify(exactly = 0) { arrivals.arrivals(any()) }
    }

    @Test
    fun `arrivals come soonest first with no estimate last`() {
        givenStops("72")
        givenArrivals(arrival("27", minutes = null), arrival("14", minutes = 5), arrival("5", minutes = 0), arrival("45", minutes = 12))

        val result = getArrivals("72")

        assertThat(result.arrivals.map { it.line }).containsExactly("5", "14", "45", "27")
    }

    @Test
    fun `ties go by line number, then by label for lines that are not a number`() {
        givenStops("72")
        val labels = listOf("N1", "M3", "14", "C03", "5", "001")
        givenArrivals(*labels.map { arrival(it, minutes = 3) }.toTypedArray(), *labels.map { arrival(it, minutes = null) }.toTypedArray())

        val result = getArrivals("72")

        val sorted = listOf("001", "5", "14", "C03", "M3", "N1")
        assertThat(result.arrivals.map { it.line }).containsExactlyElementsOf(sorted + sorted)
        assertThat(result.arrivals.takeLast(sorted.size)).allSatisfy { assertThat(it.noEstimate).isTrue() }
    }

    @Test
    fun `freshness is the arrivals', not the stop list's`() {
        givenStops("72")
        every { arrivals.arrivals("72") } returns ArrivalSnapshot(emptyList(), UPDATED_AT, stale = true)

        val result = getArrivals("72")

        assertThat(result.arrivals).isEmpty()
        assertThat(result.updatedAt).isEqualTo(UPDATED_AT)
        assertThat(result.stale).isTrue()
    }

    private fun givenStops(vararg number: String) {
        val list = number.map { BusStop(it, "Stop $it", GeoPoint(40.42, -3.69), listOf("5")) }
        every { stops.snapshot() } returns BusStopSnapshot(list, Instant.parse("2026-10-29T10:00:00Z"), stale = false)
    }

    private fun givenArrivals(vararg arrival: Arrival) {
        every { arrivals.arrivals("72") } returns ArrivalSnapshot(arrival.toList(), UPDATED_AT, stale = false)
    }

    private fun arrival(
        line: String,
        minutes: Int?,
    ) = Arrival(
        line = line,
        destination = "SOL",
        minutes = minutes,
        noEstimate = minutes == null,
        distanceMeters = minutes?.let { it * 300 },
        bus = minutes?.let { GeoPoint(40.42, -3.69) },
    )

    private companion object {
        val UPDATED_AT: Instant = Instant.parse("2026-10-30T10:15:00Z")
    }
}
