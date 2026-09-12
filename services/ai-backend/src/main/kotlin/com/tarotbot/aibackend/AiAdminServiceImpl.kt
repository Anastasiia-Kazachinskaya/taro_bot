package com.tarotbot.aibackend

import com.tarotbot.aibackend.config.ProviderRegistry
import com.tarotbot.proto.ai.AiAdminServiceGrpcKt
import com.tarotbot.proto.ai.GetProviderStatusRequest
import com.tarotbot.proto.ai.ProviderStatus
import com.tarotbot.proto.ai.SetActiveProviderRequest
import com.tarotbot.proto.ai.providerStatus
import io.grpc.Status
import io.grpc.StatusException

/** Lets an operator flip primary/fallback at runtime, without a redeploy. */
class AiAdminServiceImpl(private val registry: ProviderRegistry) :
    AiAdminServiceGrpcKt.AiAdminServiceCoroutineImplBase() {

    override suspend fun setActiveProvider(request: SetActiveProviderRequest): ProviderStatus {
        try {
            registry.setActive(request.primary, request.fallback)
        } catch (e: IllegalArgumentException) {
            throw StatusException(Status.INVALID_ARGUMENT.withDescription(e.message))
        }
        return currentStatus()
    }

    override suspend fun getProviderStatus(request: GetProviderStatusRequest): ProviderStatus = currentStatus()

    private fun currentStatus(): ProviderStatus {
        val (primary, fallback) = registry.status()
        return providerStatus {
            this.primary = primary
            this.fallback = fallback
        }
    }
}
