package com.tarotbot.resilience

import java.time.Duration

/** Which gRPC statuses are safe to retry automatically — never on caller-error codes. */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoff: Duration = Duration.ofMillis(500),
    val maxBackoff: Duration = Duration.ofSeconds(4),
    val backoffMultiplier: Double = 2.0,
    val retryableStatusCodes: Set<String> = setOf("UNAVAILABLE", "DEADLINE_EXCEEDED"),
)

data class CircuitBreakerPolicy(
    val failureRateThreshold: Float = 50f,
    val slidingWindowSize: Int = 20,
    val minimumNumberOfCalls: Int = 10,
    val waitDurationInOpenState: Duration = Duration.ofSeconds(15),
    val permittedCallsInHalfOpenState: Int = 5,
)

/**
 * Bundles everything a resilient outbound gRPC channel needs: a default
 * per-call deadline, a retry policy, and a circuit breaker. Every service
 * builds its channels through [ResilientChannelFactory] with one of these
 * instead of hand-rolling retry/timeout logic per call site.
 */
data class ChannelPolicy(
    val deadline: Duration,
    val retry: RetryPolicy = RetryPolicy(),
    val circuitBreaker: CircuitBreakerPolicy = CircuitBreakerPolicy(),
) {
    companion object {
        /** Master -> DB backend: fast, mostly single-row Postgres operations. */
        fun forDbBackend() = ChannelPolicy(deadline = Duration.ofSeconds(10))

        /** Master -> AI backend: the LLM call itself can legitimately take a while. */
        fun forAiBackend() = ChannelPolicy(
            deadline = Duration.ofSeconds(65),
            circuitBreaker = CircuitBreakerPolicy(waitDurationInOpenState = Duration.ofSeconds(30)),
        )

        /** Gateway -> Master backend. */
        fun forMasterBackend() = ChannelPolicy(deadline = Duration.ofSeconds(15))
    }
}
