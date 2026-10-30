package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusStop
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import io.github.jorgetroya80.madtransit.shared.emt.EmtResponse
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
class EmtBusStopMapperTest {
    @Test
    fun `fixture stops are translated to the domain with public line labels`() {
        val stops = EmtBusStopMapper.toDomain(fixtureStops(), fixtureLines())

        assertThat(stops).containsExactly(
            BusStop(
                number = "72",
                name = "Cibeles-Casa de América",
                location = GeoPoint(40.420268054530624, -3.692139136326868),
                lines = listOf("14", "150", "27", "37", "45", "5", "53", "C03", "N1", "N22", "N24", "N25", "N26", "N4"),
            ),
            BusStop(
                number = "2115",
                name = "Plaza Santa Cruz-Plaza Mayor",
                location = GeoPoint(40.41513710448409, -3.705616121396342),
                lines = listOf("M3"),
            ),
            BusStop(
                number = "2768",
                name = "Emigrantes-Carretera de Canillas",
                location = GeoPoint(40.46529114072302, -3.636305450659557),
                lines = listOf("73", "N3"),
            ),
            BusStop(
                number = "4025",
                name = "Benavente",
                location = GeoPoint(40.41469907672767, -3.702833610585003),
                lines = listOf("002", "M1", "M3"),
            ),
            BusStop(
                number = "1",
                name = "Avenida Valdemarín-Blanca de Castilla",
                location = GeoPoint(40.470050286124454, -3.782875407208744),
                lines = listOf("161", "999"),
            ),
        )
    }

    @Test
    fun `both directions of a line give a single label`() {
        val stop = EmtBusStopMapper.toDomain(listOf(emtStop(lines = listOf("501/1", "501/2"))), fixtureLines()).single()

        assertThat(stop.lines).containsExactly("N1")
    }

    @Test
    fun `unknown line code is kept without its direction and logged once`(output: CapturedOutput) {
        EmtBusStopMapper.toDomain(fixtureStops(), fixtureLines())

        assertThat(output.out.lines().filter { "WARN" in it }).singleElement().satisfies({
            assertThat(it).contains("999", "stop=1")
        })
    }

    @Test
    fun `line code without direction is looked up as is`() {
        val stop = EmtBusStopMapper.toDomain(listOf(emtStop(lines = listOf("073"))), fixtureLines()).single()

        assertThat(stop.lines).containsExactly("73")
    }

    @Test
    fun `stop without lines has no lines`() {
        val stop = EmtBusStopMapper.toDomain(listOf(emtStop(lines = null)), fixtureLines()).single()

        assertThat(stop.lines).isEmpty()
    }

    @Test
    fun `stop without geometry is discarded with a warning`(output: CapturedOutput) {
        val stops = EmtBusStopMapper.toDomain(listOf(emtStop(node = "1"), emtStop(node = "88", geometry = null)), fixtureLines())

        assertThat(stops.map { it.number }).containsExactly("1")
        assertThat(output.out.lines().filter { "WARN" in it && "88" in it }).hasSize(1)
    }

    @Test
    fun `stop with incomplete coordinates is discarded`() {
        val stops = EmtBusStopMapper.toDomain(listOf(emtStop(geometry = EmtGeometry(listOf(-3.7)))), fixtureLines())

        assertThat(stops).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("-3.7, 91.0", "-180.5, 40.4", "NaN, 40.4")
    fun `stop with invalid coordinates is discarded with a warning`(
        lon: Double,
        lat: Double,
        output: CapturedOutput,
    ) {
        val stops =
            EmtBusStopMapper.toDomain(
                listOf(emtStop(node = "1"), emtStop(node = "99", geometry = EmtGeometry(listOf(lon, lat)))),
                fixtureLines(),
            )

        assertThat(stops.map { it.number }).containsExactly("1")
        assertThat(output.out.lines().filter { "WARN" in it && "99" in it }).hasSize(1)
    }

    @ParameterizedTest
    @CsvSource(value = ["null", "''", "'  '"], nullValues = ["null"])
    fun `stop without number is discarded with a warning`(
        node: String?,
        output: CapturedOutput,
    ) {
        val stops = EmtBusStopMapper.toDomain(listOf(emtStop(node = node, name = "Sin número")), fixtureLines())

        assertThat(stops).isEmpty()
        assertThat(output.out.lines().filter { "WARN" in it && "Sin número" in it }).hasSize(1)
    }

    @Test
    fun `stop without name is discarded with a warning`(output: CapturedOutput) {
        val stops = EmtBusStopMapper.toDomain(listOf(emtStop(node = "77", name = null)), fixtureLines())

        assertThat(stops).isEmpty()
        assertThat(output.out.lines().filter { "WARN" in it && "77" in it }).hasSize(1)
    }

    @Test
    fun `catalog entries without code or label are ignored`() {
        val lines = listOf(EmtLineInfo(line = null, label = "X"), EmtLineInfo(line = "073", label = null))

        val stop = EmtBusStopMapper.toDomain(listOf(emtStop(lines = listOf("073/1"))), lines).single()

        assertThat(stop.lines).containsExactly("073")
    }

    private fun fixtureStops(): List<EmtBusStop> = readFixture("/emt/bus-stops-list.json")

    private fun fixtureLines(): List<EmtLineInfo> = readFixture("/emt/bus-lines-info.json")

    private inline fun <reified T> readFixture(path: String): List<T> {
        val json = javaClass.getResourceAsStream(path)
        return jacksonObjectMapper().readValue<EmtResponse<T>>(json).data.orEmpty()
    }

    private fun emtStop(
        node: String? = "1",
        name: String? = "Puerta del Sol",
        geometry: EmtGeometry? = EmtGeometry(listOf(-3.7038, 40.4168)),
        lines: List<String>? = listOf("073/2"),
    ) = EmtBusStop(node = node, name = name, geometry = geometry, lines = lines)
}
