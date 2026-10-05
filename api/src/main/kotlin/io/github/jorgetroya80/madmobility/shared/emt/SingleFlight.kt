package io.github.jorgetroya80.madmobility.shared.emt

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs one [run] block per key at a time: concurrent callers with the same key wait for it and get
 * its result or its original exception. The key is free again as soon as the block finishes.
 */
internal class SingleFlight<V : Any> {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<V>>()

    fun run(
        key: String,
        block: () -> V,
    ): V {
        val mine = CompletableFuture<V>()
        val running = inFlight.putIfAbsent(key, mine)
        if (running != null) return join(running)
        try {
            return block().also(mine::complete)
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(key, mine)
        }
    }

    private fun join(running: CompletableFuture<V>): V =
        try {
            running.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }
}
