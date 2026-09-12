package com.tarotbot.observability

import io.grpc.ServerBuilder
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.protobuf.services.HealthStatusManager
import io.grpc.protobuf.services.ProtoReflectionService
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** Health/metrics wiring shared by every gRPC service, installed once from `main()`. */
object Observability {

    /**
     * Registers a Prometheus meter registry, adds a request-metrics interceptor plus
     * gRPC health/reflection services to [grpcServerBuilder], and starts a small Ktor
     * HTTP server on [metricsPort] exposing `/metrics` (Prometheus scrape) and
     * `/healthz` (plain 200 OK, for container/compose health checks).
     */
    fun install(
        serviceName: String,
        grpcServerBuilder: ServerBuilder<*>,
        metricsPort: Int,
    ): Installed {
        val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        val healthStatusManager = HealthStatusManager()
        healthStatusManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING)

        grpcServerBuilder
            .intercept(MetricsServerInterceptor(serviceName, registry))
            .addService(ProtoReflectionService.newInstance())
            .addService(healthStatusManager.healthService)

        val httpServer = embeddedServer(CIO, port = metricsPort) {
            observabilityRouting(registry)
        }.start(wait = false)

        return Installed(registry, healthStatusManager, httpServer)
    }

    data class Installed(
        val registry: PrometheusMeterRegistry,
        val healthStatusManager: HealthStatusManager,
        val httpServer: ApplicationEngine,
    )
}

/** Exposed separately so it can be exercised with Ktor's test host without a gRPC server. */
fun Application.observabilityRouting(registry: PrometheusMeterRegistry) {
    routing {
        get("/metrics") { call.respondText(registry.scrape(), ContentType.Text.Plain) }
        get("/healthz") { call.respondText("OK", ContentType.Text.Plain) }
    }
}
