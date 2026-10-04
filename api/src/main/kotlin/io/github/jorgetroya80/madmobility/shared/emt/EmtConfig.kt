package io.github.jorgetroya80.madmobility.shared.emt

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Clock
import java.time.ZoneId

@Configuration
@EnableConfigurationProperties(EmtProperties::class)
class EmtConfig {
    /** EMT dates, quota resets and logs are in Madrid time. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Europe/Madrid"))

    @Bean
    fun emtRestClient(
        builder: RestClient.Builder,
        properties: EmtProperties,
    ): RestClient = emtRestClient(builder, properties.baseUrl.toString(), properties)

    companion object {
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
