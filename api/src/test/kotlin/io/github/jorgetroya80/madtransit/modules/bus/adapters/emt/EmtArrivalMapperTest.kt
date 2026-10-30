package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.Arrival
import io.github.jorgetroya80.madtransit.modules.bus.domain.GeoPoint
import io.github.jorgetroya80.madtransit.shared.emt.EmtResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@ExtendWith(OutputCaptureExtension::class)
class EmtArrivalMapperTest {
    @Test
    fun `SC3 stop 72 gives its 6 arrivals with 999999 as no estimate and no bus position`() {
        val arrivals = EmtArrivalMapper.toDomain("72", fixtureArrivals())

        assertThat(arrivals.map { it.line }).containsExactly("14", "45", "37", "53", "5", "C03")
        assertThat(arrivals.first()).isEqualTo(
            Arrival(
                line = "14",
                destination = "PIO XII",
                minutes = 0,
                noEstimate = false,
                distanceMeters = 0,
                bus = GeoPoint(40.41441008140411, -3.6930564972057955),
            ),
        )
        assertThat(arrivals.last()).isEqualTo(
            Arrival(line = "C03", destination = "ARGÜELLES", minutes = null, noEstimate = true, distanceMeters = null, bus = null),
        )
        assertThat(arrivals.dropLast(1)).allSatisfy {
            assertThat(it.noEstimate).isFalse()
            assertThat(it.bus).isNotNull()
        }
    }

    @Test
    fun `minutes are rounded down from the fixture seconds`() {
        val arrivals = EmtArrivalMapper.toDomain("72", fixtureArrivals())

        assertThat(arrivals.map { it.minutes }).containsExactly(0, 0, 2, 23, 25, null)
        assertThat(arrivals.map { it.distanceMeters }).containsExactly(0, 140, 450, 3565, 2788, null)
    }

    @ParameterizedTest(name = "{0} s is {1} min")
    @CsvSource("0, 0", "55, 0", "59, 0", "60, 1", "1553, 25", "2623, 43", "999998, 16666")
    fun `an estimate is whole minutes rounded down`(
        seconds: Int,
        minutes: Int,
    ) {
        val arrival = EmtArrivalMapper.toDomain("72", listOf(arrive(estimateArrive = seconds))).single()

        assertThat(arrival.minutes).isEqualTo(minutes)
        assertThat(arrival.noEstimate).isFalse()
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("999999", "1000000")
    fun `999999 seconds or more is no estimate, without distance or position`(seconds: Int) {
        val arrival = EmtArrivalMapper.toDomain("72", listOf(arrive(estimateArrive = seconds))).single()

        assertThat(arrival.minutes).isNull()
        assertThat(arrival.noEstimate).isTrue()
        assertThat(arrival.distanceMeters).isNull()
        assertThat(arrival.bus).isNull()
    }

    @Test
    fun `a missing estimate is no estimate`() {
        val arrival = EmtArrivalMapper.toDomain("72", listOf(arrive(estimateArrive = null))).single()

        assertThat(arrival.noEstimate).isTrue()
        assertThat(arrival.minutes).isNull()
    }

    @Test
    fun `the bus position comes from the GeoJSON lon, lat`() {
        val arrival = EmtArrivalMapper.toDomain("72", listOf(arrive(geometry = EmtGeometry(listOf(-3.6935, 40.4199))))).single()

        assertThat(arrival.bus).isEqualTo(GeoPoint(40.4199, -3.6935))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unknownPositions")
    fun `a bus without valid coordinates keeps its estimate and has no position`(geometry: EmtGeometry?) {
        val arrival = EmtArrivalMapper.toDomain("72", listOf(arrive(geometry = geometry))).single()

        assertThat(arrival.bus).isNull()
        assertThat(arrival.minutes).isEqualTo(2)
    }

    @Test
    fun `a missing distance is null`() {
        val arrival = EmtArrivalMapper.toDomain("72", listOf(arrive(distanceBus = null))).single()

        assertThat(arrival.distanceMeters).isNull()
        assertThat(arrival.minutes).isEqualTo(2)
    }

    @Test
    fun `an arrival without line or destination is discarded and logged`(output: CapturedOutput) {
        val arrivals =
            EmtArrivalMapper.toDomain(
                "72",
                listOf(arrive(line = null), arrive(destination = null), arrive(line = "27")),
            )

        assertThat(arrivals.map { it.line }).containsExactly("27")
        assertThat(output.out.lines().filter { "WARN" in it }).hasSize(2).allSatisfy {
            assertThat(it).contains("stop=72")
        }
    }

    private fun fixtureArrivals(): List<EmtArrive> {
        val json = requireNotNull(javaClass.getResource("/emt/bus-arrivals.json")) { "missing fixture" }.readText()
        return jacksonObjectMapper()
            .readValue<EmtResponse<EmtArrivesData>>(json)
            .data
            .orEmpty()
            .single()
            .arrive
            .orEmpty()
    }

    private fun arrive(
        line: String? = "14",
        destination: String? = "PIO XII",
        estimateArrive: Int? = 168,
        distanceBus: Int? = 450,
        geometry: EmtGeometry? = EmtGeometry(listOf(-3.69, 40.41)),
    ) = EmtArrive(line, destination, estimateArrive, distanceBus, geometry)

    private companion object {
        @JvmStatic
        fun unknownPositions() =
            listOf(
                null,
                EmtGeometry(null),
                EmtGeometry(listOf(0.0, 0.0)),
                EmtGeometry(listOf(-3.69)),
                EmtGeometry(listOf(-3.69, 40.41, 650.0)),
                EmtGeometry(listOf(-3.69, 91.0)),
            )
    }
}
