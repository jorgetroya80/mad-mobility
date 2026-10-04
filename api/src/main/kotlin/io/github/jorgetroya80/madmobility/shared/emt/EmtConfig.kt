package io.github.jorgetroya80.madmobility.shared.emt

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Clock
import java.time.ZoneId

@Configuration
@EnableConfigurationProperties(EmtProperties::class, QuotaProperties::class, CacheProperties::class)
class EmtConfig {
    /** EMT dates, quota resets and logs are in Madrid time. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Europe/Madrid"))

    @Bean
    fun emtCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker = registry.circuitBreaker(RESILIENCE_INSTANCE)

    @Bean
    fun emtRetry(registry: RetryRegistry): Retry = registry.retry(RESILIENCE_INSTANCE)

    @Bean
    fun emtRestClient(
        builder: RestClient.Builder,
        properties: EmtProperties,
    ): RestClient = emtRestClient(builder, properties.baseUrl.toString(), properties)

    companion object {
        /** Name of the Resilience4j instances configured under resilience4j.* in application.yaml */
        const val RESILIENCE_INSTANCE = "emt"

        fun emtRestClient(
            builder: RestClient.Builder,
            baseUrl: String,
            properties: EmtProperties,
        ): RestClient {
            val httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout).build()
            val requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(properties.readTimeout) }
            return builder.baseUrl(baseUrl).requestFactory(requestFactory).build()
        }
    }
}
