package io.github.jorgetroya80.madmobility.modules.bicimad.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StationTest {
    @Test
    fun `stations are ordered by the numeric part of the number and then by its suffix`() {
        val stations = listOf("10", "5b", "5", "5a", "100", "9").map { station(it) }

        assertThat(stations.sortedWith(Station.BY_NUMBER).map { it.number }).containsExactly("5", "5a", "5b", "9", "10", "100")
    }

    @Test
    fun `numbers without a numeric part go last`() {
        val stations = listOf("X", "7").map { station(it) }

        assertThat(stations.sortedWith(Station.BY_NUMBER).map { it.number }).containsExactly("7", "X")
    }

    @Test
    fun `an operational station with bikes is available for bikes, not for docks when it has none free`() {
        val station = station("1", bikes = 3, freeDocks = 0)

        assertThat(station.isAvailableFor(Need.BIKES)).isTrue()
        assertThat(station.isAvailableFor(Need.DOCKS)).isFalse()
    }

    @Test
    fun `a station out of service is available for nothing`() {
        val station = station("1", status = StationStatus.NO_SERVICE)

        assertThat(Need.entries.map(station::isAvailableFor)).containsOnly(false)
    }

    private fun station(
        number: String,
        bikes: Int = 1,
        freeDocks: Int = 1,
        status: StationStatus = StationStatus.OPERATIONAL,
    ) = Station(
        id = 1,
        number = number,
        name = "Station $number",
        address = "Address",
        location = GeoPoint(40.4168, -3.7038),
        bikes = bikes,
        freeDocks = freeDocks,
        totalDocks = bikes + freeDocks,
        status = status,
        occupancy = Occupancy.LOW,
    )
}
