package com.tarotbot.masterbackend

import com.tarotbot.config.EnvConfig
import com.tarotbot.masterbackend.orchestration.AiClient
import com.tarotbot.masterbackend.orchestration.DbClient
import com.tarotbot.masterbackend.orchestration.MasterConfig
import com.tarotbot.masterbackend.orchestration.MasterOrchestrator
import com.tarotbot.observability.Observability
import com.tarotbot.proto.ai.AiServiceGrpcKt
import com.tarotbot.proto.db.DbServiceGrpcKt
import com.tarotbot.resilience.ChannelPolicy
import com.tarotbot.resilience.ResilientChannelFactory
import io.grpc.ServerBuilder

fun main() {
    val env = System.getenv()
    val grpcPort = EnvConfig.intEnv("GRPC_PORT", 9090, env)
    val metricsPort = EnvConfig.intEnv("METRICS_PORT", 9190, env)
    val dbTarget = EnvConfig.requiredEnv("DB_BACKEND_TARGET", env)
    val aiTarget = EnvConfig.requiredEnv("AI_BACKEND_TARGET", env)

    val config = MasterConfig(
        readingsLimit = EnvConfig.intEnv("DAILY_READINGS_LIMIT", 10, env),
        llmLimit = EnvConfig.intEnv("DAILY_LLM_LIMIT", 30, env),
        unlimitedUserIds = EnvConfig.idSetEnv("UNLIMITED_USER_IDS", env),
        adminUserIds = EnvConfig.idSetEnv("ADMIN_USER_IDS", env),
    )

    val dbChannel = ResilientChannelFactory.build(dbTarget, ChannelPolicy.forDbBackend())
    val aiChannel = ResilientChannelFactory.build(aiTarget, ChannelPolicy.forAiBackend())
    val orchestrator = MasterOrchestrator(
        DbClient(DbServiceGrpcKt.DbServiceCoroutineStub(dbChannel)),
        AiClient(AiServiceGrpcKt.AiServiceCoroutineStub(aiChannel)),
        config,
    )

    val serverBuilder = ServerBuilder.forPort(grpcPort)
    Observability.install("master-backend", serverBuilder, metricsPort)
    val server = serverBuilder.addService(MasterServiceImpl(orchestrator)).build().start()

    println("master-backend listening on gRPC :$grpcPort, metrics on :$metricsPort")
    server.awaitTermination()
}
