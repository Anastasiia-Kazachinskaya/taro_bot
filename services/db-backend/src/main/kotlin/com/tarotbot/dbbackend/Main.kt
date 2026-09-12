package com.tarotbot.dbbackend

import com.tarotbot.config.EnvConfig
import com.tarotbot.observability.Observability
import io.grpc.ServerBuilder

fun main() {
    val env = System.getenv()
    val grpcPort = EnvConfig.intEnv("GRPC_PORT", 9091, env)
    val metricsPort = EnvConfig.intEnv("METRICS_PORT", 9191, env)
    val runMigrations = EnvConfig.envOrDefault("RUN_MIGRATIONS", "true", env).toBoolean()

    val databases = DbConnections.fromEnv(env)

    if (runMigrations) {
        FlywayMigrator.migrate(
            url = EnvConfig.requiredEnv("DB_PRIMARY_URL", env),
            user = EnvConfig.requiredEnv("DB_PRIMARY_USER", env),
            password = EnvConfig.requiredEnv("DB_PRIMARY_PASSWORD", env),
        )
    }

    val serverBuilder = ServerBuilder.forPort(grpcPort)
    Observability.install("db-backend", serverBuilder, metricsPort)
    val server = serverBuilder.addService(DbServiceImpl(databases)).build().start()

    println("db-backend listening on gRPC :$grpcPort, metrics on :$metricsPort")
    server.awaitTermination()
}
