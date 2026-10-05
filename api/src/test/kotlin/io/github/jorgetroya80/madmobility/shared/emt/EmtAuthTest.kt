package io.github.jorgetroya80.madmobility.shared.emt

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ExtendWith(OutputCaptureExtension::class)
class EmtAuthTest : EmtWireMockTest() {
    private val clock = MutableClock()
    private val meterRegistry = SimpleMeterRegistry()
    private val quota = QuotaTracker(QuotaProperties(), clock, meterRegistry)

    private fun auth(props: EmtProperties = properties) = EmtAuth(restClient(props), props, clock, meterRegistry, quota)

    private fun loginCount() = wireMock.findAll(getRequestedFor(urlPathEqualTo(LOGIN_PATH))).size

    @Test
    fun `logs in with email and password`() {
        stubLogin()

        assertThat(auth().accessToken()).isEqualTo(FIXTURE_TOKEN)
        wireMock.verify(
            getRequestedFor(urlPathEqualTo(LOGIN_PATH))
                .withHeader("email", equalTo(TEST_EMAIL))
                .withHeader("password", equalTo(TEST_PASSWORD)),
        )
    }

    @Test
    fun `logs in with client id and pass key`() {
        stubLogin()
        val props = EmtProperties(clientId = "my-client", passKey = "my-key")

        auth(props).accessToken()

        wireMock.verify(
            getRequestedFor(urlPathEqualTo(LOGIN_PATH))
                .withHeader("X-ClientId", equalTo("my-client"))
                .withHeader("passKey", equalTo("my-key")),
        )
    }

    @Test
    fun `reuses the token until five minutes before it expires`() {
        stubLogin() // tokenSecExpiration = 86399
        val auth = auth()

        auth.accessToken()
        clock.advance(Duration.ofSeconds(86399).minusMinutes(5).minusSeconds(1))
        auth.accessToken()
        assertThat(loginCount()).isEqualTo(1)

        clock.advance(Duration.ofSeconds(1))
        auth.accessToken()
        assertThat(loginCount()).isEqualTo(2)
    }

    @Test
    fun `concurrent callers share a single login`() {
        stubLogin(delay = Duration.ofMillis(200))
        val auth = auth()
        val start = CountDownLatch(1)
        val executor = Executors.newVirtualThreadPerTaskExecutor()

        val tokens =
            (1..50).map { executor.submit<String> { start.await().let { auth.accessToken() } } }
        start.countDown()

        assertThat(tokens.map { it.get(5, TimeUnit.SECONDS) }).containsOnly(FIXTURE_TOKEN)
        assertThat(loginCount()).isEqualTo(1)
        executor.shutdown()
    }

    @Test
    fun `invalidate forces a new login only for the rejected token`() {
        stubLogin()
        val auth = auth()
        val token = auth.accessToken()

        auth.invalidate("some-older-token")
        auth.accessToken()
        assertThat(loginCount()).isEqualTo(1)

        auth.invalidate(token)
        auth.accessToken()
        assertThat(loginCount()).isEqualTo(2)
    }

    @Test
    fun `rejected credentials throw EmtAuthFailed without leaking secrets`(output: CapturedOutput) {
        stubLogin("login-bad-credentials.json")

        assertThatThrownBy { auth().accessToken() }
            .isInstanceOf(EmtAuthFailed::class.java)
            .hasMessageContaining("code=89")
            .hasMessageNotContaining(TEST_PASSWORD)
        assertThat(output.all).doesNotContain(TEST_PASSWORD, FIXTURE_TOKEN)
        assertThat(meterRegistry.counter("emt.auth.logins", "result", "rejected").count()).isEqualTo(1.0)
    }

    @Test
    fun `successful login never logs the token`(output: CapturedOutput) {
        stubLogin()

        auth().accessToken()

        assertThat(output.all).doesNotContain(FIXTURE_TOKEN, TEST_PASSWORD)
        assertThat(meterRegistry.counter("emt.auth.logins", "result", "success").count()).isEqualTo(1.0)
    }

    @Test
    fun `server error throws EmtUnavailable`() {
        wireMock.stubFor(get(urlPathEqualTo(LOGIN_PATH)).willReturn(aResponse().withStatus(503).withBody("<html>down</html>")))

        assertThatThrownBy { auth().accessToken() }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.SERVER_ERROR)
            }
    }

    @Test
    fun `slow answer throws EmtUnavailable with TIMEOUT`() {
        stubLogin(delay = Duration.ofSeconds(1))
        val props = EmtProperties(email = TEST_EMAIL, password = TEST_PASSWORD, readTimeout = Duration.ofMillis(200))

        assertThatThrownBy { auth(props).accessToken() }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.TIMEOUT)
            }
    }

    @Test
    fun `dropped connection throws EmtUnavailable`() {
        wireMock.stubFor(get(urlPathEqualTo(LOGIN_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

        assertThatThrownBy { auth().accessToken() }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.CONNECTION_FAILED)
            }
    }

    @Test
    fun `connection failure while reading the body throws EmtUnavailable`() {
        wireMock.stubFor(get(urlPathEqualTo(LOGIN_PATH)).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)))

        assertThatThrownBy { auth().accessToken() }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.CONNECTION_FAILED)
            }
    }

    @Test
    fun `malformed JSON throws EmtProtocolError`() {
        wireMock.stubFor(
            get(urlPathEqualTo(LOGIN_PATH)).willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody("{not json"),
            ),
        )

        assertThatThrownBy { auth().accessToken() }.isInstanceOf(EmtProtocolError::class.java)
    }

    @Test
    fun `unexpected code throws EmtProtocolError`() {
        wireMock.stubFor(
            get(urlPathEqualTo(LOGIN_PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"code":"42","description":"weird","data":[]}"""),
            ),
        )

        assertThatThrownBy { auth().accessToken() }
            .isInstanceOfSatisfying(EmtProtocolError::class.java) { assertThat(it.code).isEqualTo("42") }
    }

    @Test
    fun `login consumes auth quota and records the usage reported by EMT`() {
        stubLogin()

        auth().accessToken()

        assertThat(quota.used(EmtAuth.QUOTA_MODULE)).isEqualTo(1)
        assertThat(meterRegistry.get("emt.quota.reported").gauge().value()).isEqualTo(0.0) // apiCounter.current in fixture
    }

    @Test
    fun `no login when the quota is exhausted`() {
        stubLogin()
        val exhausted = QuotaTracker(QuotaProperties(globalDailyLimit = 0), clock, SimpleMeterRegistry())

        assertThatThrownBy { EmtAuth(restClient(), properties, clock, meterRegistry, exhausted).accessToken() }
            .isInstanceOf(EmtQuotaExceeded::class.java)
        assertThat(loginCount()).isZero()
    }

    @Test
    fun `concurrent callers share a failing login`() {
        wireMock.stubFor(get(urlPathEqualTo(LOGIN_PATH)).willReturn(aResponse().withStatus(503).withFixedDelay(500)))
        val auth = auth()
        val start = CountDownLatch(1)
        val executor = Executors.newVirtualThreadPerTaskExecutor()

        val results = (1..50).map { executor.submit<Result<String>> { start.await().let { runCatching { auth.accessToken() } } } }
        start.countDown()

        assertThat(results.map { it.get(5, TimeUnit.SECONDS).exceptionOrNull() }).allSatisfy {
            assertThat(it).isInstanceOf(EmtUnavailable::class.java)
        }
        assertThat(loginCount()).isEqualTo(1)
        executor.shutdown()
    }

    @Test
    fun `rejected credentials are not retried for a minute`() {
        stubLogin("login-bad-credentials.json")
        val auth = auth()

        repeat(3) { assertThatThrownBy { auth.accessToken() }.isInstanceOf(EmtAuthFailed::class.java) }
        assertThat(loginCount()).isEqualTo(1)

        clock.advance(Duration.ofMinutes(1))
        assertThatThrownBy { auth.accessToken() }.isInstanceOf(EmtAuthFailed::class.java)
        assertThat(loginCount()).isEqualTo(2)
    }

    @Test
    fun `short-lived tokens are renewed halfway instead of on every call`() {
        wireMock.stubFor(
            get(urlPathEqualTo(LOGIN_PATH)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(fixture("login-ok.json").replace("\"tokenSecExpiration\": 86399", "\"tokenSecExpiration\": 120")),
            ),
        )
        val auth = auth()

        auth.accessToken()
        clock.advance(Duration.ofSeconds(59))
        auth.accessToken()
        assertThat(loginCount()).isEqualTo(1)

        clock.advance(Duration.ofSeconds(1))
        auth.accessToken()
        assertThat(loginCount()).isEqualTo(2)
    }
}
