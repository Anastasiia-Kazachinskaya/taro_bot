package com.tarotbot.resilience

import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder

/**
 * The single place every service builds outbound gRPC channels from. No
 * service is allowed to hand-roll its own retry loop or bare [ManagedChannel]
 * — every channel goes through here so deadline, retry and circuit-breaking
 * behavior stay consistent system-wide.
 */
object ResilientChannelFactory {

    fun build(target: String, policy: ChannelPolicy): ManagedChannel =
        NettyChannelBuilder.forTarget(target)
            .usePlaintext()
            .defaultServiceConfig(buildServiceConfig(policy.retry))
            .enableRetry()
            .intercept(DeadlineInterceptor(policy.deadline))
            .intercept(CircuitBreakingInterceptor(target, policy.circuitBreaker))
            .build()

    /**
     * gRPC's built-in retry mechanism, configured via service-config JSON
     * (as a Map, per grpc-java's [io.grpc.internal.JsonParser]-compatible
     * shape) rather than a hand-written retry loop.
     */
    private fun buildServiceConfig(retry: RetryPolicy): Map<String, Any> {
        val retryPolicy = mapOf(
            "maxAttempts" to retry.maxAttempts.toDouble(),
            "initialBackoff" to "${retry.initialBackoff.toMillis() / 1000.0}s",
            "maxBackoff" to "${retry.maxBackoff.toMillis() / 1000.0}s",
            "backoffMultiplier" to retry.backoffMultiplier,
            "retryableStatusCodes" to retry.retryableStatusCodes.toList(),
        )
        val methodConfig = mapOf(
            "name" to listOf(emptyMap<String, Any>()), // applies to all methods on this channel
            "retryPolicy" to retryPolicy,
        )
        return mapOf("methodConfig" to listOf(methodConfig))
    }
}
