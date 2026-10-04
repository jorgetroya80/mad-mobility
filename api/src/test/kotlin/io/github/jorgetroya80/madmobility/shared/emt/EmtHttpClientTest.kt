package io.github.jorgetroya80.madmobility.shared.emt

import io.github.jorgetroya80.madmobility.shared.emt.EmtWireMockTest.Companion.NO_RETRY
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/** Client logic without network (MockRestServiceServer); real HTTP behaviour lives in EmtHttpClientHttpTest. */
class EmtHttpClientTest {
    /** Subset of the real BiciMAD station fields, enough to prove the fixture deserializes. */
    data class TestStation(
        val id: Int,
        val number: String,
        val name: String,
        val light: Int,
        val activate: Int,
        val no_available: Int,
        val dock_bikes: Int,
        val free_bases: Int,
        val total_bases: Int,
        val geometry: Geometry,
    ) {
        data class Geometry(
            val type: String,
            val coordinates: List<Double>,
        )
    }

    private val builder = RestClient.builder().baseUrl("https://emt.test")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val auth = mockk<EmtAuth>(relaxUnitFun = true)
    private val client = EmtHttpClient(builder.build(), auth, CircuitBreaker.ofDefaults("test"), NO_RETRY)

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/emt/$name")).readText()

    private fun expectStations(token: String) =
        server
            .expect(once(), requestTo("https://emt.test$STATIONS"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("accessToken", token))

    @Test
    fun `returns the data array and sends the access token`() {
        every { auth.accessToken() } returns "token-1"
        expectStations("token-1").andRespond(withSuccess(fixture("bicimad-stations.json"), MediaType.APPLICATION_JSON))

        val stations = client.get("bicimad", STATIONS, TestStation::class.java)

        assertThat(stations.map { it.id }).containsExactly(1409, 1493, 2397)
        assertThat(stations.first().geometry.coordinates).containsExactly(-3.7021354, 40.4285212)
        server.verify()
    }

    @Test
    fun `logs in again once when the token is rejected`() {
        every { auth.accessToken() } returnsMany listOf("expired", "fresh")
        expectStations("expired").andRespond(
            withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(fixture("token-invalid.json")),
        )
        expectStations("fresh").andRespond(withSuccess(fixture("bicimad-stations.json"), MediaType.APPLICATION_JSON))

        assertThat(client.get("bicimad", STATIONS, TestStation::class.java)).hasSize(3)
        verify(exactly = 1) { auth.invalidate("expired") }
        server.verify()
    }

    @Test
    fun `gives up when a fresh token is rejected too`() {
        every { auth.accessToken() } returnsMany listOf("expired", "fresh")
        val invalid = withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(fixture("token-invalid.json"))
        expectStations("expired").andRespond(invalid)
        expectStations("fresh").andRespond(invalid)

        assertThatThrownBy { client.get("bicimad", STATIONS, TestStation::class.java) }
            .isInstanceOfSatisfying(EmtProtocolError::class.java) { assertThat(it.code).isEqualTo("80") }
        server.verify()
    }

    @Test
    fun `unknown code on HTTP 200 throws EmtProtocolError with the code`() {
        every { auth.accessToken() } returns "token-1"
        expectStations("token-1").andRespond(
            withSuccess("""{"code":"42","description":"weird","data":[]}""", MediaType.APPLICATION_JSON),
        )

        assertThatThrownBy { client.get("bicimad", STATIONS, TestStation::class.java) }
            .isInstanceOfSatisfying(EmtProtocolError::class.java) { assertThat(it.code).isEqualTo("42") }
            .hasMessageContaining("weird")
    }

    @Test
    fun `server error throws EmtUnavailable`() {
        every { auth.accessToken() } returns "token-1"
        expectStations("token-1").andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("<html>oops</html>"))

        assertThatThrownBy { client.get("bicimad", STATIONS, TestStation::class.java) }
            .isInstanceOfSatisfying(EmtUnavailable::class.java) {
                assertThat(it.reason).isEqualTo(EmtUnavailable.Reason.SERVER_ERROR)
            }
    }

    @Test
    fun `non JSON client error throws EmtProtocolError`() {
        every { auth.accessToken() } returns "token-1"
        expectStations("token-1").andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body("<html>404</html>"))

        assertThatThrownBy { client.get("bicimad", STATIONS, TestStation::class.java) }.isInstanceOf(EmtProtocolError::class.java)
    }

    @Test
    fun `missing data returns an empty list`() {
        every { auth.accessToken() } returns "token-1"
        expectStations("token-1").andRespond(withSuccess("""{"code":"00","description":"ok"}""", MediaType.APPLICATION_JSON))

        assertThat(client.get("bicimad", STATIONS, TestStation::class.java)).isEmpty()
    }

    private companion object {
        const val STATIONS = "/v1/transport/bicimad/stations/"
    }
}
