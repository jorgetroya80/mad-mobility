package io.github.jorgetroya80.madmobility.shared.web

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.CacheControl
import org.springframework.web.filter.ShallowEtagHeaderFilter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.WebContentInterceptor

/**
 * HTTP caching for the public API under /v1: weak ETag from the body (only 2xx, so problems are
 * never 304) and `no-cache`, so clients always revalidate instead of showing data older than the server cache.
 */
@Configuration
class HttpCacheConfig : WebMvcConfigurer {
    @Bean
    fun etagFilter(): FilterRegistrationBean<ShallowEtagHeaderFilter> =
        FilterRegistrationBean(ShallowEtagHeaderFilter().apply { isWriteWeakETag = true }).apply {
            addUrlPatterns(API_URL_PATTERN)
            // After RequestIdFilter, so a 304 still carries X-Request-Id
            order = Ordered.HIGHEST_PRECEDENCE + 1
        }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(WebContentInterceptor().apply { addCacheMapping(CacheControl.noCache(), API_PATH_PATTERN) })
    }

    private companion object {
        const val API_URL_PATTERN = "/v1/*"
        const val API_PATH_PATTERN = "/v1/**"
    }
}
