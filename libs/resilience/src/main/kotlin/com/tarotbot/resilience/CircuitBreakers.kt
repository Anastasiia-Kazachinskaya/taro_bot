package com.tarotbot.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig

/** Builds a per-target resilience4j [CircuitBreaker] from a [CircuitBreakerPolicy]. */
object CircuitBreakers {
    fun create(name: String, policy: CircuitBreakerPolicy): CircuitBreaker =
        CircuitBreaker.of(
            name,
            CircuitBreakerConfig.custom()
                .failureRateThreshold(policy.failureRateThreshold)
                .slidingWindowSize(policy.slidingWindowSize)
                .minimumNumberOfCalls(policy.minimumNumberOfCalls)
                .waitDurationInOpenState(policy.waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(policy.permittedCallsInHalfOpenState)
                .build(),
        )
}
