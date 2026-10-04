package io.github.jorgetroya80.madmobility.shared.emt

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

@ConfigurationProperties("mad-mobility.cache")
data class CacheProperties(
    /** How long a value is served without calling the loader. */
    val ttl: Duration = Duration.ofSeconds(60),
    /** How old a value may be when it is served as stale because the EMT failed. */
    val maxStale: Duration = Duration.ofHours(24),
    val modules: Map<String, ModuleCache> = emptyMap(),
) {
    data class ModuleCache(
        val ttl: Duration? = null,
        val maxStale: Duration? = null,
    )

    fun ttl(module: String): Duration = modules[module]?.ttl ?: ttl

    fun maxStale(module: String): Duration = modules[module]?.maxStale ?: maxStale
}

/** A cached value; [stale] means the EMT failed and this is the last good value from [updatedAt]. */
data class Cached<T>(
    val value: T,
    val updatedAt: Instant,
    val stale: Boolean,
)

/**
 * Per-module cache in front of the EMT: fresh values within the TTL, one loader call per key at a
 * time (single-flight) and, when the loader fails with an [EmtException], the last good value
 * marked as stale (up to `max-stale` old).
 */
@Component
class CacheService(
    private val properties: CacheProperties,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    private val store: Cache<String, Cached<Any>> = Caffeine.newBuilder().maximumSize(MAX_ENTRIES).build()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<Cached<Any>>>()

    fun <T : Any> get(
        module: String,
        key: String,
        loader: () -> T,
    ): Cached<T> {
        val cacheKey = "$module:$key"
        val entry = store.getIfPresent(cacheKey)
        if (entry != null && isFresh(entry, module)) return record(module, Result.HIT, entry)
        return try {
            record(module, Result.MISS, refresh(cacheKey, module, loader))
        } catch (e: EmtException) {
            entry
                ?.takeIf { age(it) <= properties.maxStale(module) }
                ?.copy(stale = true)
                ?.let { record(module, Result.STALE, it) }
                ?: throw e
        }
    }

    /** Runs [loader] once per key even with concurrent callers; they all share its result or failure. */
    private fun refresh(
        cacheKey: String,
        module: String,
        loader: () -> Any,
    ): Cached<Any> {
        val mine = CompletableFuture<Cached<Any>>()
        val running = inFlight.putIfAbsent(cacheKey, mine)
        if (running != null) return join(running)
        try {
            // Another caller may have refreshed between our read and taking ownership
            val loaded =
                store.getIfPresent(cacheKey)?.takeIf { isFresh(it, module) }
                    ?: Cached(loader(), clock.instant(), stale = false).also { store.put(cacheKey, it) }
            mine.complete(loaded)
            return loaded
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(cacheKey, mine)
        }
    }

    private fun join(running: CompletableFuture<Cached<Any>>): Cached<Any> =
        try {
            running.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }

    private fun isFresh(
        entry: Cached<Any>,
        module: String,
    ): Boolean = age(entry) < properties.ttl(module)

    private fun age(entry: Cached<Any>): Duration = Duration.between(entry.updatedAt, clock.instant())

    @Suppress("UNCHECKED_CAST")
    private fun <T> record(
        module: String,
        result: Result,
        entry: Cached<Any>,
    ): Cached<T> {
        meterRegistry.counter("emt.cache.gets", "module", module, "result", result.tag).increment()
        return entry as Cached<T>
    }

    private enum class Result(
        val tag: String,
    ) {
        HIT("hit"),
        MISS("miss"),
        STALE("stale"),
    }

    private companion object {
        const val MAX_ENTRIES = 10_000L
    }
}
