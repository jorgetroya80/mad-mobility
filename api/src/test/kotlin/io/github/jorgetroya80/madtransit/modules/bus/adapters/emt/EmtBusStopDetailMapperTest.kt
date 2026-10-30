package io.github.jorgetroya80.madtransit.modules.bus.adapters.emt

import io.github.jorgetroya80.madtransit.modules.bus.domain.BusLine
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
import java.time.DayOfWeek

@ExtendWith(OutputCaptureExtension::class)
class EmtBusStopDetailMapperTest {
    @Test
    fun `SC2 stop 72 gives its address and lines with headers, direction, schedule and frequency in EMT order`() {
        val detail = EmtBusStopDetailMapper.toDomain(fixture("bus-stop-detail-72.json"), DayOfWeek.FRIDAY)

        assertThat(detail.address).isEqualTo("Pº de Recoletos, 2 (Pza. de Cibeles)")
        assertThat(detail.lines.map { it.label }).containsExactly("5", "14", "N1", "N25")
        assertThat(detail.lines.first()).isEqualTo(
            BusLine(
                label = "5",
                headerA = "SOL/SEVILLA",
                headerB = "CHAMARTIN",
                direction = "B",
                firstBus = "06:30",
                lastBus = "22:58",
                minFrequencyMinutes = 10,
                maxFrequencyMinutes = 22,
            ),
        )
    }

    @Test
    fun `night lines cross midnight as they come`() {
        val detail = EmtBusStopDetailMapper.toDomain(fixture("bus-stop-detail-72.json"), DayOfWeek.FRIDAY)

        val n1 = detail.lines.single { it.label == "N1" }
        assertThat(n1.firstBus).isEqualTo("23:30")
        assertThat(n1.lastBus).isEqualTo("05:30")
        assertThat(n1.minFrequencyMinutes).isEqualTo(15)
        assertThat(n1.maxFrequencyMinutes).isEqualTo(25)
    }

    @Test
    fun `stop 2768 gives 73 and N3, each with the only day type the EMT sent`() {
        val detail = EmtBusStopDetailMapper.toDomain(fixture("bus-stop-detail.json"), DayOfWeek.MONDAY)

        assertThat(detail.address).isEqualTo("Ctra. de Canillas con C/ Emigrantes")
        assertThat(detail.lines.map { it.label }).containsExactly("73", "N3")
        assertThat(detail.lines.map { it.firstBus }).containsExactly("06:15", "00:00")
        assertThat(detail.lines.map { it.direction }).containsExactly("A", "A")
    }

    @ParameterizedTest(name = "{0} picks {1}")
    @CsvSource(
        "MONDAY, 06:10",
        "THURSDAY, 06:10",
        "FRIDAY, 06:20",
        "SATURDAY, 07:00",
        "SUNDAY, 08:00",
    )
    fun `a line with several day types gets today's`(
        today: DayOfWeek,
        firstBus: String,
    ) {
        val stop =
            emtStop(
                line(dayType = "LA", startTime = "06:10"),
                line(dayType = "VI", startTime = "06:20"),
                line(dayType = "SA", startTime = "07:00"),
                line(dayType = "FE", startTime = "08:00"),
            )

        val line = EmtBusStopDetailMapper.toDomain(stop, today).lines.single()

        assertThat(line.firstBus).isEqualTo(firstBus)
    }

    @Test
    fun `on a Friday without VI the workday schedule is used`() {
        val stop = emtStop(line(dayType = "SA", startTime = "07:00"), line(dayType = "LA", startTime = "06:10"))

        val line = EmtBusStopDetailMapper.toDomain(stop, DayOfWeek.FRIDAY).lines.single()

        assertThat(line.firstBus).isEqualTo("06:10")
    }

    @Test
    fun `a line with several day types but none for today keeps its headers with no schedule`() {
        val stop = emtStop(line(dayType = "SA"), line(dayType = "FE"))

        val line = EmtBusStopDetailMapper.toDomain(stop, DayOfWeek.TUESDAY).lines.single()

        assertThat(line).isEqualTo(
            BusLine(
                label = "5",
                headerA = "SOL/SEVILLA",
                headerB = "CHAMARTIN",
                direction = "B",
                firstBus = null,
                lastBus = null,
                minFrequencyMinutes = null,
                maxFrequencyMinutes = null,
            ),
        )
    }

    @Test
    fun `lines keep the EMT order when their day types are interleaved`() {
        val stop =
            emtStop(
                line(code = "005", label = "5", dayType = "LA"),
                line(code = "014", label = "14", dayType = "LA"),
                line(code = "005", label = "5", dayType = "SA"),
            )

        val lines = EmtBusStopDetailMapper.toDomain(stop, DayOfWeek.MONDAY).lines

        assertThat(lines.map { it.label }).containsExactly("5", "14")
    }

    @Test
    fun `an unreadable frequency is null and logged`(output: CapturedOutput) {
        val stop = emtStop(line(minFreq = "abc", maxFreq = " 22 "))

        val line = EmtBusStopDetailMapper.toDomain(stop, DayOfWeek.MONDAY).lines.single()

        assertThat(line.minFrequencyMinutes).isNull()
        assertThat(line.maxFrequencyMinutes).isEqualTo(22)
        assertThat(output.out.lines().filter { "WARN" in it }).singleElement().satisfies({
            assertThat(it).contains("abc", "stop=72", "line=5")
        })
    }

    @Test
    fun `a missing frequency is null without a warning`(output: CapturedOutput) {
        val line = EmtBusStopDetailMapper.toDomain(emtStop(line(minFreq = null)), DayOfWeek.MONDAY).lines.single()

        assertThat(line.minFrequencyMinutes).isNull()
        assertThat(output.out).doesNotContain("WARN")
    }

    @Test
    fun `a line without label, headers or direction is discarded and logged`(output: CapturedOutput) {
        val stop =
            emtStop(
                line(code = "001", label = null),
                line(code = "002", headerA = null),
                line(code = "003", headerB = null),
                line(code = "004", direction = null),
                line(code = "005", label = "5"),
            )

        val lines = EmtBusStopDetailMapper.toDomain(stop, DayOfWeek.MONDAY).lines

        assertThat(lines.map { it.label }).containsExactly("5")
        assertThat(output.out.lines().filter { "WARN" in it }).hasSize(4)
    }

    @Test
    fun `the address is trimmed and a blank one is null`() {
        assertThat(
            EmtBusStopDetailMapper.toDomain(emtStop(address = "  Calle Mayor, 1 "), DayOfWeek.MONDAY).address,
        ).isEqualTo("Calle Mayor, 1")
        assertThat(EmtBusStopDetailMapper.toDomain(emtStop(address = " "), DayOfWeek.MONDAY).address).isNull()
        assertThat(EmtBusStopDetailMapper.toDomain(emtStop(address = null), DayOfWeek.MONDAY).address).isNull()
    }

    @Test
    fun `a stop without lines has no lines`() {
        val stop = emtStop().copy(dataLine = null)

        assertThat(EmtBusStopDetailMapper.toDomain(stop, DayOfWeek.MONDAY).lines).isEmpty()
    }

    private fun fixture(name: String): EmtBusStopDetail {
        val json = requireNotNull(javaClass.getResource("/emt/$name")) { "missing fixture $name" }.readText()
        return jacksonObjectMapper()
            .readValue<EmtResponse<EmtBusStopDetailData>>(json)
            .data
            .orEmpty()
            .single()
            .stops
            .orEmpty()
            .single()
    }

    private fun emtStop(
        vararg lines: EmtStopDetailLine,
        address: String? = "Pº de Recoletos, 2",
    ) = EmtBusStopDetail(stop = "72", name = "Cibeles", postalAddress = address, geometry = null, dataLine = lines.toList())

    private fun line(
        code: String? = "005",
        label: String? = "5",
        headerA: String? = "SOL/SEVILLA",
        headerB: String? = "CHAMARTIN",
        direction: String? = "B",
        dayType: String? = "LA",
        startTime: String? = "06:30",
        minFreq: String? = "10",
        maxFreq: String? = "22",
    ) = EmtStopDetailLine(
        line = code,
        label = label,
        direction = direction,
        headerA = headerA,
        headerB = headerB,
        startTime = startTime,
        stopTime = "22:58",
        minFreq = minFreq,
        maxFreq = maxFreq,
        dayType = dayType,
    )
}
