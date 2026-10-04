package io.github.jorgetroya80.madmobility.shared.emt

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@ConfigurationProperties("mad-mobility.quota")
data class QuotaProperties(
    /** Safety margin below the EMT daily quota of 20,000 calls. */
    val globalDailyLimit: Int = 18_000,
    /** Optional per-module caps, e.g. mad-mobility.quota.modules.bicimad.daily-limit. */
    val modules: Map<String, ModuleQuota> = emptyMap(),
) {
    data class ModuleQuota(
        val dailyLimit: Int,
    )
}

/**
 * Daily EMT call budget per module and in total, kept in memory and reset at midnight in the
 * [Clock]'s zone (Europe/Madrid). Every EMT request must call [tryAcquire] first.
 */
@Component
class QuotaTracker(
    private val properties: QuotaProperties,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    private val lock = ReentrantLock()
    private var day: LocalDate = today()
    private val global = AtomicInteger()
    private val perModule = ConcurrentHashMap<String, AtomicInteger>()
    private val reportedByEmt = AtomicLong(-1)

    init {
        Gauge.builder("emt.quota.used", global) { it.get().toDouble() }.tag("module", ALL).register(meterRegistry)
        Gauge.builder("emt.quota.limit") { properties.globalDailyLimit.toDouble() }.tag("module", ALL).register(meterRegistry)
        Gauge
            .builder("emt.quota.reported", reportedByEmt) { it.get().toDouble() }
            .description("Daily usage reported by the EMT login (apiCounter.current); -1 until the first login")
            .register(meterRegistry)
    }

    /** Reserves one EMT call for [module]; false when the module or global daily limit is reached. */
    fun tryAcquire(module: String): Boolean =
        lock.withLock {
            rollOverIfNewDay()
            val moduleCount = counter(module)
            if (global.get() >= properties.globalDailyLimit || moduleCount.get() >= limit(module)) {
                false
            } else {
                global.incrementAndGet()
                moduleCount.incrementAndGet()
                true
            }
        }

    fun used(module: String): Int = lock.withLock { rollOverIfNewDay().let { perModule[module]?.get() ?: 0 } }

    fun usedTotal(): Int = lock.withLock { rollOverIfNewDay().let { global.get() } }

    fun limit(module: String): Int = properties.modules[module]?.dailyLimit ?: properties.globalDailyLimit

    /** When the counters reset: next midnight in the clock's zone. */
    fun resetsAt(): Instant =
        today()
            .plusDays(1)
            .atStartOfDay(clock.zone)
            .toInstant()

    /** Records the usage the EMT reports on login, to compare it with the local counters. */
    fun reportEmtUsage(current: Long) = reportedByEmt.set(current)

    private fun counter(module: String): AtomicInteger =
        perModule.computeIfAbsent(module) { name ->
            AtomicInteger().also { count ->
                Gauge.builder("emt.quota.used", count) { it.get().toDouble() }.tag("module", name).register(meterRegistry)
                Gauge.builder("emt.quota.limit") { limit(name).toDouble() }.tag("module", name).register(meterRegistry)
            }
        }

    private fun rollOverIfNewDay() {
        val today = today()
        if (today != day) {
            day = today
            global.set(0)
            perModule.values.forEach { it.set(0) }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        const val ALL = "all"
    }
}
