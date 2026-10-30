package io.github.jorgetroya80.madtransit.modules.bus.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BusStopIndexTest {
    private val index = BusStopIndex(listOf(stop("72"), stop("2768")))

    @Test
    fun `finds a stop by its number`() {
        assertThat(index.find("2768")).isEqualTo(stop("2768"))
    }

    @Test
    fun `a number that is not in the list finds nothing`() {
        assertThat(index.find("072")).isNull()
    }

    @Test
    fun `keeps the stops in their order`() {
        assertThat(index.stops.map { it.number }).containsExactly("72", "2768")
    }

    private fun stop(number: String) = BusStop(number, "Stop $number", GeoPoint(40.42, -3.69), listOf("5"))
}
