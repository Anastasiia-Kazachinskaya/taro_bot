package com.tarotbot.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.concurrent.Callable

class CircuitBreakerTest : FunSpec({

    fun failingCallable(): Callable<Unit> = Callable { throw RuntimeException("boom") }

    test("opens after enough failing calls and then rejects new calls") {
        val policy = CircuitBreakerPolicy(
            failureRateThreshold = 50f,
            slidingWindowSize = 4,
            minimumNumberOfCalls = 4,
            waitDurationInOpenState = Duration.ofSeconds(30),
            permittedCallsInHalfOpenState = 2,
        )
        val breaker = CircuitBreakers.create("test-target", policy)

        repeat(4) {
            runCatching { breaker.executeCallable(failingCallable()) }
        }

        breaker.state shouldBe CircuitBreaker.State.OPEN
        breaker.tryAcquirePermission() shouldBe false
    }

    test("stays closed while failures are below the minimum call count") {
        val policy = CircuitBreakerPolicy(
            failureRateThreshold = 50f,
            slidingWindowSize = 10,
            minimumNumberOfCalls = 10,
            waitDurationInOpenState = Duration.ofSeconds(30),
            permittedCallsInHalfOpenState = 2,
        )
        val breaker = CircuitBreakers.create("test-target-2", policy)

        repeat(3) {
            runCatching { breaker.executeCallable(failingCallable()) }
        }

        breaker.state shouldBe CircuitBreaker.State.CLOSED
        breaker.tryAcquirePermission() shouldBe true
    }
})
