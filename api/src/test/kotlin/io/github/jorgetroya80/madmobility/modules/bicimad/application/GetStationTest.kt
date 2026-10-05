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
    fun `returns the station with the given id`() {
        given(station(id = 1), station(id = 2))

        val result = getStation(2)

        assertThat(result.station).isEqualTo(station(id = 2))
    }

    @Test
    fun `passes the snapshot freshness through`() {
        given(station(id = 1), stale = true)

        val result = getStation(1)

        assertThat(result.updatedAt).isEqualTo(UPDATED_AT)
        assertThat(result.stale).isTrue()
    }

    @Test
    fun `unknown id is not found`() {
        given(station(id = 1))

        assertThatThrownBy { getStation(99) }.isInstanceOf(StationNotFound::class.java).hasMessageContaining("99")
    }

    private fun given(
        vararg stations: Station,
        stale: Boolean = false,
    ) {
        every { provider.snapshot() } returns StationSnapshot(stations.toList(), UPDATED_AT, stale)
    }

    private fun station(id: Int) =
        Station(
            id = id,
            number = "$id",
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
