package com.tarotbot.observability

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.TimeUnit

/** Records request count/latency per RPC method+status into the given registry. */
class MetricsServerInterceptor(
    private val serviceName: String,
    private val registry: MeterRegistry,
) : ServerInterceptor {

    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val startNanos = System.nanoTime()
        val method = call.methodDescriptor.fullMethodName

        val wrapped = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            override fun close(status: Status, trailers: Metadata) {
                val elapsed = System.nanoTime() - startNanos
                registry.timer(
                    "grpc_server_requests",
                    "service", serviceName,
                    "method", method,
                    "status", status.code.name,
                ).record(elapsed, TimeUnit.NANOSECONDS)
                super.close(status, trailers)
            }
        }

        return next.startCall(wrapped, headers)
    }
}
