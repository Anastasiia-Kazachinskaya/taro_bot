package com.tarotbot.gateway

import com.tarotbot.config.EnvConfig
import com.tarotbot.gateway.telegram.TelegramHttpClient
import com.tarotbot.observability.Observability
import com.tarotbot.proto.gateway.MasterServiceGrpcKt
import com.tarotbot.resilience.ChannelPolicy
import com.tarotbot.resilience.ResilientChannelFactory
import io.grpc.ServerBuilder
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val env = System.getenv()
    val botToken = EnvConfig.requiredEnv("BOT_TOKEN", env)
    val masterTarget = EnvConfig.requiredEnv("MASTER_BACKEND_TARGET", env)
    val grpcPort = EnvConfig.intEnv("GRPC_PORT", 9100, env)
    val metricsPort = EnvConfig.intEnv("METRICS_PORT", 9101, env)

    val telegram = TelegramHttpClient(botToken)
    val masterChannel = ResilientChannelFactory.build(masterTarget, ChannelPolicy.forMasterBackend())
    val masterStub = MasterServiceGrpcKt.MasterServiceCoroutineStub(masterChannel)

    // The gateway is a pure client of Telegram + Master, with no gRPC service of its
    // own — this server exposes only health/reflection so it's observable the same
    // way as the other three services (a live process with nothing to add is still
    // a meaningful health signal).
    val serverBuilder = ServerBuilder.forPort(grpcPort)
    Observability.install("gateway", serverBuilder, metricsPort)
    serverBuilder.build().start()

    println("gateway polling Telegram, health on gRPC :$grpcPort, metrics on :$metricsPort")
    PollingLoop(telegram, masterStub).run()
}
