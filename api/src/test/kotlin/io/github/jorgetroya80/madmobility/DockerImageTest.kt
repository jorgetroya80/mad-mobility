package io.github.jorgetroya80.madmobility

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * SC14: builds api/Dockerfile with the Docker CLI (BuildKit, needed for cache mounts) and checks the
 * container serves /health/live as a non-root user. Needs Docker; run with ./gradlew dockerImageTest.
 */
@Tag("docker")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DockerImageTest {
    private lateinit var container: GenericContainer<*>

    @BeforeAll
    fun buildAndStart() {
        val build =
            ProcessBuilder("docker", "build", "--quiet", "--tag", IMAGE, ".")
                .redirectErrorStream(true)
                .start()
        val output = build.inputStream.bufferedReader().readText()
        check(build.waitFor(10, TimeUnit.MINUTES) && build.exitValue() == 0) { "docker build failed:\n$output" }

        container =
            GenericContainer(DockerImageName.parse(IMAGE))
                .withExposedPorts(8080)
                .withEnv("EMT_EMAIL", "image-test@example.com")
                .withEnv("EMT_PASSWORD", "image-test-password")
                .waitingFor(Wait.forHttp("/health/live").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(1)))
        container.start()
    }

    @AfterAll
    fun stop() {
        if (::container.isInitialized) container.stop()
    }

    @Test
    fun `serves liveness and readiness`() {
        listOf("/health/live", "/health/ready").forEach { path ->
            val response =
                HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://${container.host}:${container.getMappedPort(8080)}$path")).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.body()).contains("\"UP\"")
        }
    }

    @Test
    fun `runs as a non-root user that cannot modify the application`() {
        assertThat(container.execInContainer("id", "-u").stdout.trim()).isEqualTo("10001")
        assertThat(container.execInContainer("touch", "/app/app.jar").exitCode).isNotZero()
    }

    private companion object {
        const val IMAGE = "mad-mobility-api:test"
    }
}
