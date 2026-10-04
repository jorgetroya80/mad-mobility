package io.github.jorgetroya80.madmobility.shared.emt

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/**
 * "emt" component of /health: circuit breaker state, token validity and quota usage. It is not part
 * of the live/ready groups because the API keeps serving cached data while the EMT is down.
 */
@Component
class EmtHealthIndicator(
    private val emtCircuitBreaker: CircuitBreaker,
    private val emtAuth: EmtAuth,
    private val quotaTracker: QuotaTracker,
    private val quotaProperties: QuotaProperties,
) : HealthIndicator {
    override fun health(): Health {
        val state = emtCircuitBreaker.state
        val renewsAt = emtAuth.tokenRenewsAt()
        val builder = if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) Health.down() else Health.up()
        return builder
            .withDetail("circuitBreaker", state.name)
            .withDetail("tokenValid", renewsAt != null)
            .apply { renewsAt?.let { withDetail("tokenRenewsAt", it.toString()) } }
            .withDetail("quotaUsed", quotaTracker.usedTotal())
            .withDetail("quotaLimit", quotaProperties.globalDailyLimit)
            .withDetail("quotaResetsAt", quotaTracker.resetsAt().toString())
            .build()
    }
}
