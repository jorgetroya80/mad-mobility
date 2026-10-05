package io.github.jorgetroya80.madmobility.modules.bicimad.application

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

class ListStationsTest {
    private val provider = mockk<StationProvider>()

    @Test
    fun `returns the snapshot with its stations ordered by number`() {
        val updatedAt = Instant.parse("2026-10-05T10:15:00Z")
        every { provider.snapshot() } returns StationSnapshot(listOf(station("10"), station("5a"), station("5")), updatedAt, stale = true)

        val snapshot = ListStations(provider)()

        assertThat(snapshot.stations.map { it.number }).containsExactly("5", "5a", "10")
        assertThat(snapshot.updatedAt).isEqualTo(updatedAt)
        assertThat(snapshot.stale).isTrue()
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
