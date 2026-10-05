package io.github.jorgetroya80.madmobility

import io.github.jorgetroya80.madmobility.modules.bicimad.ports.StationProvider
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * SC9: writes the OpenAPI document of the public API to the file given by ./gradlew generateOpenApi.
 * The EMT is never called: building the document does not touch the stations.
 */
@Tag("openapi")
// TestEmtController (/v1/test/**) is a test fixture, not part of the public API
@SpringBootTest(properties = ["springdoc.paths-to-exclude=/v1/test/**"])
@AutoConfigureMockMvc
class OpenApiExportTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val jsonMapper: JsonMapper,
    @Autowired private val buildProperties: BuildProperties,
) {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun stationProvider(): StationProvider = mockk()
    }

    @Test
    fun `exports the public API document`() {
        val result = mvc.get().uri(API_DOCS).exchange()

        assertThat(result).hasStatusOk()
        val document = jsonMapper.readTree(result.response.contentAsString)
        assertThat(document.path("paths").propertyNames()).isNotEmpty.allMatch { it.startsWith(PUBLIC_API_PREFIX) }
        assertThat(document.path("info").path("version").asString()).isEqualTo(buildProperties.version)
        RESPONSE_SCHEMAS.map { document.path("components").path("schemas").path(it) }.forEach { schema ->
            val optional =
                schema.path("properties").propertyNames() -
                    schema
                        .path("required")
                        .values()
                        .map { it.asString() }
                        .toSet()
            assertThat(optional).isSubsetOf(OPTIONAL_PROPERTIES)
        }
        val file = Path.of(checkNotNull(System.getProperty(OUTPUT_PROPERTY)) { "Run with ./gradlew generateOpenApi" })
        Files.createDirectories(file.parent)
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, document)
    }

    private companion object {
        const val API_DOCS = "/v3/api-docs"
        const val PUBLIC_API_PREFIX = "/v1/"
        const val OUTPUT_PROPERTY = "openapi.output"
        val RESPONSE_SCHEMAS = listOf("StationsResponse", "StationDetailResponse", "StationResponse")

        // Kotlin nullable properties of the responses; every other property is always present
        val OPTIONAL_PROPERTIES = setOf("distanceMeters")
    }
}
