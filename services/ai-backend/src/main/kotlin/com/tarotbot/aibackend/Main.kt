package com.tarotbot.aibackend

import com.tarotbot.aibackend.config.ProviderRegistry
import com.tarotbot.config.EnvConfig
import com.tarotbot.observability.Observability
import io.grpc.ServerBuilder
import java.nio.file.Paths

fun main() {
    val env = System.getenv()
    val grpcPort = EnvConfig.intEnv("GRPC_PORT", 9092, env)
    val metricsPort = EnvConfig.intEnv("METRICS_PORT", 9192, env)
    val configPath = Paths.get(EnvConfig.envOrDefault("PROVIDERS_CONFIG_PATH", "/config/providers.yaml", env))

    val registry = ProviderRegistry(configPath, env = env)

    val serverBuilder = ServerBuilder.forPort(grpcPort)
    Observability.install("ai-backend", serverBuilder, metricsPort)
    val server = serverBuilder
        .addService(AiServiceImpl(registry))
        .addService(AiAdminServiceImpl(registry))
        .build()
        .start()

    println("ai-backend listening on gRPC :$grpcPort, metrics on :$metricsPort")
    server.awaitTermination()
}
