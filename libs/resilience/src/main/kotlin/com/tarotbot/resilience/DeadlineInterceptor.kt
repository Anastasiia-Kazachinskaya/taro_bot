package com.tarotbot.resilience

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Applies a default deadline to every call that doesn't already set a more specific one. */
class DeadlineInterceptor(private val deadline: Duration) : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        val effectiveOptions = if (callOptions.deadline == null) {
            callOptions.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
        } else {
            callOptions
        }
        return next.newCall(method, effectiveOptions)
    }
}
