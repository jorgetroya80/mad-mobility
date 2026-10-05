package io.github.jorgetroya80.madmobility.shared.emt

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
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
 * [Clock]'s zone (Europe/Madrid). Every EMT request must call [acquire] first.
 */
@Component
class QuotaTracker(
    private val properties: QuotaProperties,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    private val lock = ReentrantLock()
    private var day: LocalDate = today()
    private var global = 0
    private val perModule = HashMap<String, Int>()
    private val reportedByEmt = AtomicLong(-1)

    init {
        // Gauges read through used*/rollOver so they show 0 right after midnight, not yesterday's count
        Gauge.builder("emt.quota.used", this) { it.usedTotal().toDouble() }.tag("module", ALL).register(meterRegistry)
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
            val moduleCount = moduleCount(module)
            if (global >= properties.globalDailyLimit || moduleCount >= limit(module)) {
                false
            } else {
                global++
                perModule[module] = moduleCount + 1
                true
            }
        }

    /** Reserves one EMT call for [module] or throws [EmtQuotaExceeded] when a daily limit is reached. */
    fun acquire(module: String) {
        if (!tryAcquire(module)) throw EmtQuotaExceeded(module, resetsAt())
    }

    fun used(module: String): Int =
        lock.withLock {
            rollOverIfNewDay()
            perModule[module] ?: 0
        }

    fun usedTotal(): Int =
        lock.withLock {
            rollOverIfNewDay()
            global
        }

    fun limit(module: String): Int = properties.modules[module]?.dailyLimit ?: properties.globalDailyLimit

    /** When the counters reset: next midnight in the clock's zone. */
    fun resetsAt(): Instant =
        today()
            .plusDays(1)
            .atStartOfDay(clock.zone)
            .toInstant()

    /** Records the usage the EMT reports on login, to compare it with the local counters. */
    fun reportEmtUsage(current: Long) = reportedByEmt.set(current)

    private fun moduleCount(module: String): Int =
        perModule.getOrPut(module) {
            registerGauges(module)
            0
        }

    private fun registerGauges(module: String) {
        Gauge.builder("emt.quota.used", this) { it.used(module).toDouble() }.tag("module", module).register(meterRegistry)
        Gauge.builder("emt.quota.limit") { limit(module).toDouble() }.tag("module", module).register(meterRegistry)
    }

    private fun rollOverIfNewDay() {
        val today = today()
        if (today != day) {
            day = today
            global = 0
            perModule.replaceAll { _, _ -> 0 }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        const val ALL = "all"
    }
}
