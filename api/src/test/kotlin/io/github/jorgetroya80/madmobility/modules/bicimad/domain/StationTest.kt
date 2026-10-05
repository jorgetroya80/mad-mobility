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

    private fun station(number: String) =
        Station(
            id = 1,
            number = number,
            name = "Station $number",
            address = "Address",
            lat = 40.4168,
            lon = -3.7038,
            bikes = 1,
            freeDocks = 1,
            totalDocks = 2,
            status = StationStatus.OPERATIONAL,
            occupancy = Occupancy.LOW,
        )
}
