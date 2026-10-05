package io.github.jorgetroya80.madmobility.modules.bicimad.application

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.GeoPoint
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationSnapshot
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class GetStationTest {
    private val provider = mockk<StationProvider>()
    private val getStation = GetStation(provider)

    @Test
    fun `returns the station with the given number`() {
        given(station(id = 1, number = "5"), station(id = 2, number = "538"))

        val result = getStation("538")

        assertThat(result.station).isEqualTo(station(id = 2, number = "538"))
    }

    @Test
    fun `number ignores case`() {
        given(station(id = 1, number = "25B"), station(id = 2, number = "25A"))

        val result = getStation("25a")

        assertThat(result.station.number).isEqualTo("25A")
    }

    @Test
    fun `passes the snapshot freshness through`() {
        given(station(id = 1, number = "5"), stale = true)

        val result = getStation("5")

        assertThat(result.updatedAt).isEqualTo(UPDATED_AT)
        assertThat(result.stale).isTrue()
    }

    @Test
    fun `unknown number is not found`() {
        given(station(id = 999, number = "5"))

        assertThatThrownBy { getStation("999") }.isInstanceOf(StationNotFound::class.java).hasMessageContaining("999")
    }

    private fun given(
        vararg stations: Station,
        stale: Boolean = false,
    ) {
        every { provider.snapshot() } returns StationSnapshot(stations.toList(), UPDATED_AT, stale)
    }

    private fun station(
        id: Int,
        number: String,
    ) = Station(
        id = id,
        number = number,
        name = "Station $id",
        address = "Address",
        location = GeoPoint(40.4168, -3.7038),
        bikes = 1,
        freeDocks = 1,
        totalDocks = 2,
        status = StationStatus.OPERATIONAL,
        occupancy = Occupancy.LOW,
    )

    private companion object {
        val UPDATED_AT: Instant = Instant.parse("2026-10-05T10:15:00Z")
    }
}
