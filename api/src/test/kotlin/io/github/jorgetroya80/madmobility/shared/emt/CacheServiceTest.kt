package io.github.jorgetroya80.madmobility.shared.emt

import io.github.jorgetroya80.madmobility.shared.emt.EmtWireMockTest.MutableClock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CacheServiceTest {
    private val clock = MutableClock()
    private val meterRegistry = SimpleMeterRegistry()
    private val cache = CacheService(CacheProperties(), clock, meterRegistry) // TTL 60 s, max-stale 24 h
    private val calls = AtomicInteger()
    private val down = EmtUnavailable(EmtUnavailable.Reason.TIMEOUT, "EMT down")

    private fun load(value: String): () -> String = { calls.incrementAndGet().let { value } }

    private fun gets(result: String) = meterRegistry.counter("emt.cache.gets", "module", "bicimad", "result", result).count()

    @Test
    fun `serves the cached value within the TTL without calling the loader`() {
        val first = cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofSeconds(59))

        val second = cache.get("bicimad", "stations", load("v2"))

        assertThat(second).isEqualTo(first).extracting { it.value }.isEqualTo("v1")
        assertThat(calls.get()).isEqualTo(1)
        assertThat(gets("miss")).isEqualTo(1.0)
        assertThat(gets("hit")).isEqualTo(1.0)
    }

    @Test
    fun `reloads after the TTL`() {
        cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofSeconds(60))

        val reloaded = cache.get("bicimad", "stations", load("v2"))

        assertThat(reloaded.value).isEqualTo("v2")
        assertThat(reloaded.updatedAt).isEqualTo(clock.now)
        assertThat(reloaded.stale).isFalse()
    }

    @Test
    fun `serves the previous value as stale when the EMT fails`() {
        val original = cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofMinutes(10))

        val stale = cache.get<String>("bicimad", "stations") { throw down }

        assertThat(stale.value).isEqualTo("v1")
        assertThat(stale.stale).isTrue()
        assertThat(stale.updatedAt).isEqualTo(original.updatedAt)
        assertThat(gets("stale")).isEqualTo(1.0)
    }

    @Test
    fun `propagates the failure when there is no previous value`() {
        assertThatThrownBy { cache.get<String>("bicimad", "stations") { throw down } }.isSameAs(down)
    }

    @Test
    fun `propagates the failure when the previous value is older than max-stale`() {
        cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofHours(24).plusSeconds(1))

        assertThatThrownBy { cache.get<String>("bicimad", "stations") { throw down } }.isSameAs(down)
    }

    @Test
    fun `programming errors are never hidden behind stale data`() {
        cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofMinutes(5))

        assertThatThrownBy { cache.get<String>("bicimad", "stations") { error("bug") } }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `concurrent reads after the TTL call the loader once`() {
        cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofMinutes(2))
        calls.set(0)
        val release = CountDownLatch(1)
        val slowLoader = {
            calls.incrementAndGet()
            release.await(5, TimeUnit.SECONDS)
            "v2"
        }
        val executor = Executors.newVirtualThreadPerTaskExecutor()

        val results = (1..50).map { executor.submit<Cached<String>> { cache.get("bicimad", "stations", slowLoader) } }
        Thread.sleep(200) // let all callers queue behind the first loader
        release.countDown()

        assertThat(results.map { it.get(5, TimeUnit.SECONDS).value }).containsOnly("v2")
        assertThat(calls.get()).isEqualTo(1)
        executor.shutdown()
    }

    @Test
    fun `concurrent callers share a failing load and all get stale data`() {
        cache.get("bicimad", "stations", load("v1"))
        clock.advance(Duration.ofMinutes(2))
        calls.set(0)
        val release = CountDownLatch(1)
        val failingLoader: () -> String = {
            calls.incrementAndGet()
            release.await(5, TimeUnit.SECONDS)
            throw down
        }
        val executor = Executors.newVirtualThreadPerTaskExecutor()

        val results = (1..20).map { executor.submit<Cached<String>> { cache.get("bicimad", "stations", failingLoader) } }
        Thread.sleep(200)
        release.countDown()

        assertThat(results.map { it.get(5, TimeUnit.SECONDS) }).allSatisfy {
            assertThat(it.value).isEqualTo("v1")
            assertThat(it.stale).isTrue()
        }
        assertThat(calls.get()).isEqualTo(1)
        executor.shutdown()
    }

    @Test
    fun `module settings override the defaults and keys are isolated per module`() {
        val properties = CacheProperties(modules = mapOf("bus" to CacheProperties.ModuleCache(ttl = Duration.ofSeconds(20))))
        val cache = CacheService(properties, clock, meterRegistry)
        cache.get("bus", "arrivals", load("bus-1"))
        cache.get("bicimad", "arrivals", load("bike-1"))
        clock.advance(Duration.ofSeconds(30))

        assertThat(cache.get("bus", "arrivals", load("bus-2")).value).isEqualTo("bus-2")
        assertThat(cache.get("bicimad", "arrivals", load("bike-2")).value).isEqualTo("bike-1")
    }
}
