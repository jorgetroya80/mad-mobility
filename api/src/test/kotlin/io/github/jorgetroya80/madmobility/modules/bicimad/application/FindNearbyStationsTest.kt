package io.github.jorgetroya80.madmobility.modules.bicimad.application

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.GeoPoint
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Need
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class FindNearbyStationsTest {
    private val provider = mockk<StationProvider>()
    private val findNearbyStations = FindNearbyStations(provider)

    @Test
    fun `without an area every station is returned by number, without distance, with the snapshot freshness`() {
        given(station("10"), station("5a"), station("5"))

        val result = findNearbyStations()

        assertThat(result.stations.map { it.station.number }).containsExactly("5", "5a", "10")
        assertThat(result.stations.map { it.distanceMeters }).containsOnlyNulls()
        assertThat(result.updatedAt).isEqualTo(UPDATED_AT)
        assertThat(result.stale).isTrue()
    }

    @Test
    fun `with an area only stations within the radius are returned, nearest first, with their distance`() {
        given(station("1", latOffset = FAR), station("2", latOffset = MIDDLE), station("3", latOffset = NEAR))

        val result = findNearbyStations(SearchArea(SOL, RADIUS_METERS))

        assertThat(result.stations.map { it.station.number }).containsExactly("3", "2")
        assertThat(result.stations.map { it.distanceMeters }).allSatisfy { assertThat(it).isNotNull().isLessThanOrEqualTo(RADIUS_METERS) }
        assertThat(result.stations.first().distanceMeters).isLessThan(result.stations.last().distanceMeters)
    }

    @Test
    fun `a station exactly at the radius is inside the area`() {
        val edge = station("1", latOffset = MIDDLE)
        given(edge)

        val result = findNearbyStations(SearchArea(SOL, SOL.distanceTo(edge.location)))

        assertThat(result.stations).hasSize(1)
    }

    @Test
    fun `need docks puts operational stations with free docks first, then the rest by distance`() {
        given(
            station("1", latOffset = NEAR, freeDocks = 0),
            station("2", latOffset = MIDDLE, freeDocks = 3),
            station("3", latOffset = NEAR / 2, freeDocks = 5, status = StationStatus.NO_SERVICE),
            station("4", latOffset = NEAR * 2, freeDocks = 1),
        )

        val result = findNearbyStations(SearchArea(SOL, RADIUS_METERS), Need.DOCKS)

        assertThat(result.stations.map { it.station.number }).containsExactly("4", "2", "3", "1")
    }

    @Test
    fun `need bikes puts operational stations with bikes first, then the rest by number`() {
        given(
            station("1", bikes = 0),
            station("2", bikes = 4, status = StationStatus.NO_SERVICE),
            station("3", bikes = 2),
            station("10", bikes = 1),
        )

        val result = findNearbyStations(need = Need.BIKES)

        assertThat(result.stations.map { it.station.number }).containsExactly("3", "10", "1", "2")
    }

    @Test
    fun `need only orders, it never removes stations`() {
        given(station("1", bikes = 0, freeDocks = 0), station("2"), station("3", status = StationStatus.NO_SERVICE))

        val withoutNeed = findNearbyStations()
        val withNeed = findNearbyStations(need = Need.DOCKS)

        assertThat(withNeed.stations).hasSize(3).hasSameSizeAs(withoutNeed.stations)
    }

    private fun given(vararg stations: Station) {
        every { provider.snapshot() } returns StationSnapshot(stations.toList(), UPDATED_AT, stale = true)
    }

    private fun station(
        number: String,
        latOffset: Double = 0.0,
        bikes: Int = 1,
        freeDocks: Int = 1,
        status: StationStatus = StationStatus.OPERATIONAL,
    ) = Station(
        id = number.hashCode(),
        number = number,
        name = "Station $number",
        address = "Address",
        location = GeoPoint(SOL.lat + latOffset, SOL.lon),
        bikes = bikes,
        freeDocks = freeDocks,
        totalDocks = bikes + freeDocks,
        status = status,
        occupancy = Occupancy.LOW,
    )

    private companion object {
        val UPDATED_AT: Instant = Instant.parse("2026-10-05T10:15:00Z")
        val SOL = GeoPoint(40.4168, -3.7038)
        const val RADIUS_METERS = 500.0

        // Degrees of latitude north of Sol: about 111 m, 334 m and 1,112 m
        const val NEAR = 0.001
        const val MIDDLE = 0.003
        const val FAR = 0.01
    }
}
