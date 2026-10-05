package io.github.jorgetroya80.madmobility.shared.emt

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {
    private val flight = SingleFlight<String>()
    private val calls = AtomicInteger()
    private val release = CountDownLatch(1)

    /** Starts [count] callers of [key] and returns once all are parked: one in the block, the rest waiting for it. */
    private fun startAll(
        count: Int,
        key: String,
        block: () -> String,
    ): List<CompletableFuture<String>> {
        val futures = List(count) { CompletableFuture<String>() }
        val threads =
            futures.map { future ->
                Thread.ofVirtual().start { runCatching { flight.run(key, block) }.fold(future::complete, future::completeExceptionally) }
            }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (threads.any { it.state != Thread.State.WAITING && it.state != Thread.State.TIMED_WAITING }) {
            check(System.nanoTime() < deadline) { "callers did not block in time" }
            Thread.onSpinWait()
        }
        return futures
    }

    private fun blockingBlock(outcome: () -> String): () -> String =
        {
            calls.incrementAndGet()
            release.await(5, TimeUnit.SECONDS)
            outcome()
        }

    @Test
    fun `concurrent callers of the same key run the block once and share its result`() {
        val results = startAll(20, "k", blockingBlock { "v" })
        release.countDown()

        assertThat(results.map { it.get(5, TimeUnit.SECONDS) }).containsOnly("v")
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `waiting callers get the original exception, not a CompletionException`() {
        val failure = EmtUnavailable(EmtUnavailable.Reason.TIMEOUT, "EMT down")
        val results = startAll(20, "k", blockingBlock { throw failure })
        release.countDown()

        results.forEach { result ->
            assertThatThrownBy { result.get(5, TimeUnit.SECONDS) }
                .isInstanceOf(ExecutionException::class.java)
                .cause()
                .isSameAs(failure)
        }
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `the key is free again after a failure`() {
        assertThatThrownBy { flight.run("k") { throw IllegalStateException("boom") } }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(flight.run("k") { "v" }).isEqualTo("v")
    }

    @Test
    fun `different keys do not wait for each other`() {
        val slow = startAll(1, "slow", blockingBlock { "slow" })

        assertThat(flight.run("fast") { "fast" }).isEqualTo("fast")
        release.countDown()
        assertThat(slow.single().get(5, TimeUnit.SECONDS)).isEqualTo("slow")
    }
}
