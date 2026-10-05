package io.github.jorgetroya80.madmobility.shared.emt

import io.github.jorgetroya80.madmobility.shared.emt.EmtWireMockTest.MutableClock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors

class QuotaTrackerTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val clock = MutableClock(madridTime("2026-10-04T23:59:58"))
    private val meterRegistry = SimpleMeterRegistry()

    private fun madridTime(local: String): Instant = ZonedDateTime.of(java.time.LocalDateTime.parse(local), madrid).toInstant()

    private fun tracker(properties: QuotaProperties) = QuotaTracker(properties, clock, meterRegistry)

    @Test
    fun `stops at the module limit`() {
        val tracker = tracker(QuotaProperties(modules = mapOf("bicimad" to QuotaProperties.ModuleQuota(2))))

        assertThat((1..3).map { tracker.tryAcquire("bicimad") }).containsExactly(true, true, false)
        assertThat(tracker.tryAcquire("bus")).isTrue()
        assertThat(tracker.used("bicimad")).isEqualTo(2)
    }

    @Test
    fun `stops at the global limit across modules`() {
        val tracker = tracker(QuotaProperties(globalDailyLimit = 3))

        assertThat(listOf("bicimad", "bus", "auth", "bicimad").map(tracker::tryAcquire)).containsExactly(true, true, true, false)
        assertThat(tracker.usedTotal()).isEqualTo(3)
    }

    @Test
    fun `acquire throws quota exceeded once the limit is reached`() {
        val tracker = tracker(QuotaProperties(modules = mapOf("bicimad" to QuotaProperties.ModuleQuota(1))))
        tracker.acquire("bicimad")

        assertThatThrownBy { tracker.acquire("bicimad") }
            .isInstanceOfSatisfying(EmtQuotaExceeded::class.java) {
                assertThat(it.module).isEqualTo("bicimad")
                assertThat(it.resetsAt).isEqualTo(madridTime("2026-10-05T00:00:00"))
            }
    }

    @Test
    fun `counters reset at midnight in Madrid`() {
        val tracker = tracker(QuotaProperties(globalDailyLimit = 1))
        tracker.tryAcquire("bicimad")
        clock.advance(Duration.ofSeconds(1)) // 23:59:59
        assertThat(tracker.tryAcquire("bicimad")).isFalse()

        clock.advance(Duration.ofSeconds(1)) // 00:00:00

        assertThat(tracker.used("bicimad")).isZero()
        assertThat(tracker.tryAcquire("bicimad")).isTrue()
    }

    @Test
    fun `day boundary follows Madrid time on the daylight saving change`() {
        // 2026-10-25 has 25 hours in Madrid (clocks go back at 03:00)
        clock.now = madridTime("2026-10-25T00:30:00")
        val tracker = tracker(QuotaProperties(globalDailyLimit = 1))
        tracker.tryAcquire("bicimad")

        clock.now = madridTime("2026-10-25T23:59:59")
        assertThat(tracker.tryAcquire("bicimad")).isFalse()

        clock.now = madridTime("2026-10-26T00:00:00")
        assertThat(tracker.tryAcquire("bicimad")).isTrue()
    }

    @Test
    fun `resets at next Madrid midnight`() {
        assertThat(tracker(QuotaProperties()).resetsAt()).isEqualTo(madridTime("2026-10-05T00:00:00"))
    }

    @Test
    fun `publishes used and limit per module`() {
        val tracker = tracker(QuotaProperties(modules = mapOf("bicimad" to QuotaProperties.ModuleQuota(5))))
        tracker.tryAcquire("bicimad")

        assertThat(
            meterRegistry
                .get("emt.quota.used")
                .tag("module", "bicimad")
                .gauge()
                .value(),
        ).isEqualTo(1.0)
        assertThat(
            meterRegistry
                .get("emt.quota.limit")
                .tag("module", "bicimad")
                .gauge()
                .value(),
        ).isEqualTo(5.0)
        assertThat(
            meterRegistry
                .get("emt.quota.limit")
                .tag("module", "all")
                .gauge()
                .value(),
        ).isEqualTo(18_000.0)
    }

    @Test
    fun `module gauge counts repeated use and reads zero after midnight`() {
        val tracker = tracker(QuotaProperties())
        repeat(3) { tracker.tryAcquire("bicimad") }
        val used = meterRegistry.find("emt.quota.used").tag("module", "bicimad").gauges()
        assertThat(used).hasSize(1)
        assertThat(used.single().value()).isEqualTo(3.0)

        clock.advance(Duration.ofSeconds(2)) // 00:00:00

        assertThat(used.single().value()).isZero()
        assertThat(
            meterRegistry
                .get("emt.quota.used")
                .tag("module", "all")
                .gauge()
                .value(),
        ).isZero()
    }

    @Test
    fun `concurrent acquires never exceed the limit`() {
        val tracker = tracker(QuotaProperties(globalDailyLimit = 50))

        val granted =
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                (1..100).map { executor.submit<Boolean> { tracker.tryAcquire("bicimad") } }.map { it.get() }
            }

        assertThat(granted.count { it }).isEqualTo(50)
        assertThat(tracker.usedTotal()).isEqualTo(50)
    }
}
