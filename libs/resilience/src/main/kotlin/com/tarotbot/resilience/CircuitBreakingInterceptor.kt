package com.tarotbot.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.util.concurrent.TimeUnit

/**
 * Wraps outbound calls to one target with a resilience4j circuit breaker.
 * gRPC has no native breaker, so this interceptor is the shared mechanism
 * every service goes through via [ResilientChannelFactory]. When the
 * breaker is open, calls fail fast as UNAVAILABLE without hitting the wire.
 */
class CircuitBreakingInterceptor(target: String, policy: CircuitBreakerPolicy) : ClientInterceptor {

    private val circuitBreaker: CircuitBreaker = CircuitBreakers.create(target, policy)

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        if (!circuitBreaker.tryAcquirePermission()) {
            return FailingClientCall(
                Status.UNAVAILABLE.withDescription("Circuit breaker open for ${method.fullMethodName}"),
            )
        }
        return CircuitBreakerClientCall(next.newCall(method, callOptions), circuitBreaker)
    }
}

private class CircuitBreakerClientCall<ReqT, RespT>(
    call: ClientCall<ReqT, RespT>,
    private val circuitBreaker: CircuitBreaker,
) : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {

    private val startNanos = System.nanoTime()

    override fun start(responseListener: Listener<RespT>, headers: Metadata) {
        val listener = object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
            override fun onClose(status: Status, trailers: Metadata) {
                val elapsed = System.nanoTime() - startNanos
                if (status.isOk) {
                    circuitBreaker.onSuccess(elapsed, TimeUnit.NANOSECONDS)
                } else {
                    circuitBreaker.onError(elapsed, TimeUnit.NANOSECONDS, status.asRuntimeException())
                }
                super.onClose(status, trailers)
            }
        }
        super.start(listener, headers)
    }
}

/** Fails immediately with [status], used when the breaker denies the call. */
private class FailingClientCall<ReqT, RespT>(private val status: Status) : ClientCall<ReqT, RespT>() {
    override fun start(responseListener: Listener<RespT>, headers: Metadata) {
        responseListener.onClose(status, Metadata())
    }
    override fun request(numMessages: Int) = Unit
    override fun cancel(message: String?, cause: Throwable?) = Unit
    override fun halfClose() = Unit
    override fun sendMessage(message: ReqT) = Unit
}
