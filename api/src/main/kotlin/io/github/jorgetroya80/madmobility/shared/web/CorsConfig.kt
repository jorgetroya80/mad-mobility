package io.github.jorgetroya80.madmobility.shared.web

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@ConfigurationProperties("mad-mobility.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)

// CORS for the public API under /v1; disabled when no origins are configured
@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(
    private val properties: CorsProperties,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        if (properties.allowedOrigins.isEmpty()) return
        registry
            .addMapping("/v1/**")
            .allowedOrigins(*properties.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .exposedHeaders(HttpHeaders.ETAG, RequestIdFilter.HEADER)
    }
}
