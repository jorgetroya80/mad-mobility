package io.github.jorgetroya80.madmobility.modules.bicimad.adapters.emt

import io.github.jorgetroya80.madmobility.modules.bicimad.domain.GeoPoint
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Occupancy
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.Station
import io.github.jorgetroya80.madmobility.modules.bicimad.domain.StationStatus
import io.github.jorgetroya80.madmobility.shared.emt.EmtResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@ExtendWith(OutputCaptureExtension::class)
class EmtStationMapperTest {
    @Test
    fun `fixture stations are translated to the domain`() {
        val stations = EmtStationMapper.toDomain(fixtureStations())

        assertThat(stations).containsExactly(
            Station(
                id = 1409,
                number = "5",
                name = "Fuencarral",
                address = "Calle Fuencarral nº 106",
                location = GeoPoint(40.4285212, -3.7021354),
                bikes = 2,
                freeDocks = 23,
                totalDocks = 27,
                status = StationStatus.OPERATIONAL,
                occupancy = Occupancy.LOW,
            ),
            Station(
                id = 1493,
                number = "90",
                name = "Metro Velázquez",
                address = "Calle Goya, 20",
                location = GeoPoint(40.4251493, -3.6838415999999996),
                bikes = 0,
                freeDocks = 0,
                totalDocks = 27,
                status = StationStatus.NO_SERVICE,
                occupancy = Occupancy.UNKNOWN,
            ),
            Station(
                id = 2397,
                number = "618",
                name = "Calle Paterna 55",
                address = "Calle Paterna 55",
                location = GeoPoint(40.340613, -3.68482),
                bikes = 17,
                freeDocks = 9,
                totalDocks = 19,
                status = StationStatus.OPERATIONAL,
                occupancy = Occupancy.MEDIUM,
            ),
        )
    }

    @Test
    fun `light 1 means high occupancy`() {
        val station = EmtStationMapper.toDomain(listOf(emtStation(light = 1))).single()

        assertThat(station.occupancy).isEqualTo(Occupancy.HIGH)
    }

    @Test
    fun `deactivated station is out of service`() {
        val station = EmtStationMapper.toDomain(listOf(emtStation(activate = 0))).single()

        assertThat(station.status).isEqualTo(StationStatus.NO_SERVICE)
    }

    @Test
    fun `virtually deleted station is discarded`() {
        val stations = EmtStationMapper.toDomain(listOf(emtStation(id = 1), emtStation(id = 2, virtualDelete = true)))

        assertThat(stations.map { it.id }).containsExactly(1)
    }

    @Test
    fun `unknown light gives unknown occupancy and a warning`(output: CapturedOutput) {
        val station = EmtStationMapper.toDomain(listOf(emtStation(id = 77, light = 7))).single()

        assertThat(station.occupancy).isEqualTo(Occupancy.UNKNOWN)
        assertThat(output.out.lines().filter { "WARN" in it && "77" in it }).hasSize(1)
    }

    @Test
    fun `station without geometry is discarded with a warning`(output: CapturedOutput) {
        val stations = EmtStationMapper.toDomain(listOf(emtStation(id = 1), emtStation(id = 88, geometry = null)))

        assertThat(stations.map { it.id }).containsExactly(1)
        assertThat(output.out.lines().filter { "WARN" in it && "88" in it }).hasSize(1)
    }

    @Test
    fun `station with incomplete coordinates is discarded`() {
        val stations = EmtStationMapper.toDomain(listOf(emtStation(geometry = EmtGeometry(listOf(-3.7)))))

        assertThat(stations).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("-3.7, 91.0", "-180.5, 40.4", "NaN, 40.4", "-3.7, Infinity")
    fun `station with invalid coordinates is discarded with a warning`(
        lon: Double,
        lat: Double,
        output: CapturedOutput,
    ) {
        val stations = EmtStationMapper.toDomain(listOf(emtStation(id = 1), emtStation(id = 99, geometry = EmtGeometry(listOf(lon, lat)))))

        assertThat(stations.map { it.id }).containsExactly(1)
        assertThat(output.out.lines().filter { "WARN" in it && "99" in it }).hasSize(1)
    }

    @Test
    fun `station missing a required field is discarded`() {
        val stations = EmtStationMapper.toDomain(listOf(emtStation(dockBikes = null)))

        assertThat(stations).isEmpty()
    }

    @Test
    fun `station repeating a number in another case is discarded with a warning`(output: CapturedOutput) {
        val stations =
            EmtStationMapper.toDomain(
                listOf(emtStation(id = 1, number = "25A"), emtStation(id = 2, number = "25a")),
            )

        assertThat(stations.map { it.id to it.number }).containsExactly(1 to "25A")
        assertThat(output.out.lines().filter { "WARN" in it }).singleElement().satisfies({
            assertThat(it).contains("id=2", "25a")
        })
    }

    @Test
    fun `deleted or incomplete station does not reserve its number`(output: CapturedOutput) {
        val stations =
            EmtStationMapper.toDomain(
                listOf(
                    emtStation(id = 1, number = "7", virtualDelete = true),
                    emtStation(id = 2, number = "7", dockBikes = null),
                    emtStation(id = 3, number = "7"),
                ),
            )

        assertThat(stations.map { it.id }).containsExactly(3)
        assertThat(output.out.lines().filter { "WARN" in it && "duplicate" in it }).isEmpty()
    }

    @Test
    fun `fixture stations are translated without warnings`(output: CapturedOutput) {
        EmtStationMapper.toDomain(fixtureStations())

        assertThat(output.out.lines().filter { "WARN" in it }).isEmpty()
    }

    @Test
    fun `name without the number prefix is kept as is`() {
        val station = EmtStationMapper.toDomain(listOf(emtStation(number = "5", name = "Fuencarral"))).single()

        assertThat(station.name).isEqualTo("Fuencarral")
    }

    @Test
    fun `name and address are trimmed after removing the number prefix`() {
        val station =
            EmtStationMapper
                .toDomain(
                    listOf(emtStation(number = "5", name = "5 -  Plaza del Carmen ", address = " Plaza del Carmen 1 ")),
                ).single()

        assertThat(station.name).isEqualTo("Plaza del Carmen")
        assertThat(station.address).isEqualTo("Plaza del Carmen 1")
    }

    private fun fixtureStations(): List<EmtStation> {
        val json = javaClass.getResourceAsStream("/emt/bicimad-stations.json")
        return jacksonObjectMapper().readValue<EmtResponse<EmtStation>>(json).data.orEmpty()
    }

    private fun emtStation(
        id: Int? = 1,
        number: String? = "1",
        name: String? = "1 - Puerta del Sol",
        address: String? = "Puerta del Sol 1",
        activate: Int? = 1,
        light: Int? = 0,
        virtualDelete: Boolean? = false,
        dockBikes: Int? = 5,
        geometry: EmtGeometry? = EmtGeometry(listOf(-3.7038, 40.4168)),
    ) = EmtStation(
        id = id,
        number = number,
        name = name,
        address = address,
        geometry = geometry,
        dockBikes = dockBikes,
        freeBases = 10,
        totalBases = 15,
        activate = activate,
        noAvailable = 0,
        light = light,
        virtualDelete = virtualDelete,
    )
}
