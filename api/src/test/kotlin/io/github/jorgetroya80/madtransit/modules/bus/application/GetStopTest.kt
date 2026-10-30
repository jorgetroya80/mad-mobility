package io.github.jorgetroya80.madtransit.modules.bus.application

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusLine
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetail
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopDetailSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopIndex
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopDetailProvider
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class GetStopTest {
    private val stops = mockk<BusStopProvider>()
    private val details = mockk<BusStopDetailProvider>()
    private val getStop = GetStop(stops, details)

    @Test
    fun `combines the stop from the list with its detail`() {
        givenStops(stop("2768"), stop("72"))
        givenDetail("72", DETAIL)

        val result = getStop("72")

        assertThat(result.stop).isEqualTo(stop("72"))
        assertThat(result.detail).isEqualTo(DETAIL)
    }

    @Test
    fun `a number not in the list is not found and the detail is never asked for`() {
        givenStops(stop("72"))

        assertThatThrownBy { getStop("99999999") }.isInstanceOf(BusStopNotFound::class.java).hasMessageContaining("99999999")
        verify(exactly = 0) { details.detail(any()) }
    }

    @Test
    fun `freshness is the detail's when the list is fresh and newer`() {
        givenStops(stop("72"), updatedAt = LATER)
        givenDetail("72", DETAIL, updatedAt = EARLIER, stale = true)

        val result = getStop("72")

        assertThat(result.updatedAt).isEqualTo(EARLIER)
        assertThat(result.stale).isTrue()
    }

    @Test
    fun `a stale or older list makes the result stale and as old as the list`() {
        givenStops(stop("72"), updatedAt = EARLIER, stale = true)
        givenDetail("72", DETAIL, updatedAt = LATER)

        val result = getStop("72")

        assertThat(result.updatedAt).isEqualTo(EARLIER)
        assertThat(result.stale).isTrue()
    }

    private fun givenStops(
        vararg stop: BusStop,
        updatedAt: Instant = LATER,
        stale: Boolean = false,
    ) {
        every { stops.snapshot() } returns BusStopSnapshot(BusStopIndex(stop.toList()), updatedAt, stale)
    }

    private fun givenDetail(
        number: String,
        detail: BusStopDetail,
        updatedAt: Instant = LATER,
        stale: Boolean = false,
    ) {
        every { details.detail(number) } returns BusStopDetailSnapshot(detail, updatedAt, stale)
    }

    private fun stop(number: String) = BusStop(number, "Stop $number", GeoPoint(40.42, -3.69), listOf("5"))

    private companion object {
        val LATER: Instant = Instant.parse("2026-10-30T10:15:00Z")
        val EARLIER: Instant = LATER.minus(Duration.ofHours(2))
        val DETAIL =
            BusStopDetail(
                address = "Pº de Recoletos, 2",
                lines = listOf(BusLine("5", "SOL/SEVILLA", "CHAMARTIN", "B", "06:30", "22:58", 10, 22)),
            )
    }
}
