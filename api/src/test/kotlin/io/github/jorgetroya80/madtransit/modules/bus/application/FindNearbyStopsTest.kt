package io.github.jorgetroya80.madtransit.modules.bus.application

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStopSnapshot
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import io.github.jorgetroya80.madtransit.modules.bus.ports.BusStopProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class FindNearbyStopsTest {
    private val provider = mockk<BusStopProvider>()
    private val findNearbyStops = FindNearbyStops(provider)

    @Test
    fun `without an area every stop is returned by number, without distance, with the snapshot freshness`() {
        given(stop("2115"), stop("72"), stop("1"), stop("4025"))

        val result = findNearbyStops()

        assertThat(result.stops.map { it.stop.number }).containsExactly("1", "72", "2115", "4025")
        assertThat(result.stops.map { it.distanceMeters }).containsOnlyNulls()
        assertThat(result.updatedAt).isEqualTo(UPDATED_AT)
        assertThat(result.stale).isTrue()
    }

    @Test
    fun `with an area only stops within the radius are returned, nearest first, with their distance`() {
        given(stop("1", latOffset = FAR), stop("2", latOffset = MIDDLE), stop("3", latOffset = NEAR))

        val result = findNearbyStops(SearchArea(SOL, RADIUS_METERS))

        assertThat(result.stops.map { it.stop.number }).containsExactly("3", "2")
        assertThat(result.stops.map { it.distanceMeters }).allSatisfy { assertThat(it).isNotNull().isLessThanOrEqualTo(RADIUS_METERS) }
        assertThat(result.stops.first().distanceMeters).isLessThan(result.stops.last().distanceMeters)
    }

    @Test
    fun `stops at the same distance are ordered by number`() {
        given(stop("100", latOffset = NEAR), stop("20", latOffset = NEAR), stop("3", latOffset = MIDDLE))

        val result = findNearbyStops(SearchArea(SOL, RADIUS_METERS))

        assertThat(result.stops.map { it.stop.number }).containsExactly("20", "100", "3")
    }

    @Test
    fun `a stop exactly at the radius is inside the area`() {
        val edge = stop("1", latOffset = MIDDLE)
        given(edge)

        val result = findNearbyStops(SearchArea(SOL, SOL.distanceTo(edge.location)))

        assertThat(result.stops).hasSize(1)
    }

    @Test
    fun `no stop within the radius gives an empty list`() {
        given(stop("1", latOffset = FAR))

        val result = findNearbyStops(SearchArea(SOL, RADIUS_METERS))

        assertThat(result.stops).isEmpty()
    }

    private fun given(vararg stops: BusStop) {
        every { provider.snapshot() } returns BusStopSnapshot(stops.toList(), UPDATED_AT, stale = true)
    }

    private fun stop(
        number: String,
        latOffset: Double = 0.0,
    ) = BusStop(number, "Stop $number", GeoPoint(SOL.lat + latOffset, SOL.lon), listOf("1"))

    private companion object {
        val UPDATED_AT: Instant = Instant.parse("2026-10-30T10:15:00Z")
        val SOL = GeoPoint(40.4168, -3.7038)
        const val RADIUS_METERS = 500.0

        // Degrees of latitude north of Sol: about 111 m, 334 m and 1,112 m
        const val NEAR = 0.001
        const val MIDDLE = 0.003
        const val FAR = 0.01
    }
}
