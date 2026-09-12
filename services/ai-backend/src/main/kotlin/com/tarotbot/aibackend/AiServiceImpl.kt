package com.tarotbot.aibackend

import com.tarotbot.aibackend.config.ProviderRegistry
import com.tarotbot.aibackend.prompt.PromptBuilder
import com.tarotbot.aibackend.prompt.trimTruncated
import com.tarotbot.aibackend.provider.AiProvider
import com.tarotbot.proto.ai.AiServiceGrpcKt
import com.tarotbot.proto.ai.AnswerFollowupRequest
import com.tarotbot.proto.ai.InterpretClarifyingCardRequest
import com.tarotbot.proto.ai.InterpretSpreadRequest
import com.tarotbot.proto.ai.InterpretationResponse
import com.tarotbot.proto.ai.interpretationResponse
import io.grpc.Status
import io.grpc.StatusException

class AiServiceImpl(private val registry: ProviderRegistry) : AiServiceGrpcKt.AiServiceCoroutineImplBase() {

    override suspend fun interpretSpread(request: InterpretSpreadRequest): InterpretationResponse =
        respond(PromptBuilder.forInterpretSpread(request))

    override suspend fun answerFollowup(request: AnswerFollowupRequest): InterpretationResponse =
        respond(PromptBuilder.forAnswerFollowup(request))

    override suspend fun interpretClarifyingCard(request: InterpretClarifyingCardRequest): InterpretationResponse =
        respond(PromptBuilder.forClarifyingCard(request))

    private suspend fun respond(spec: PromptBuilder.Spec): InterpretationResponse {
        val (primary, fallback) = registry.current()
        val result = callWithFallback(primary, fallback, spec)
        val truncated = result.finishReason == "length"
        val text = if (truncated) trimTruncated(result.text) else result.text
        return interpretationResponse {
            this.text = text
            this.truncated = truncated
            providerUsed = result.providerName
        }
    }

    private data class NamedChatResult(val text: String, val finishReason: String, val providerName: String)

    private suspend fun callWithFallback(
        primary: AiProvider,
        fallback: AiProvider,
        spec: PromptBuilder.Spec,
    ): NamedChatResult {
        val primaryAttempt = runCatching { primary.chat(spec.systemPrompt, spec.userPrompt, spec.maxTokens) }
        primaryAttempt.getOrNull()?.let { return NamedChatResult(it.text, it.finishReason, primary.name) }

        val fallbackAttempt = runCatching { fallback.chat(spec.systemPrompt, spec.userPrompt, spec.maxTokens) }
        fallbackAttempt.getOrNull()?.let { return NamedChatResult(it.text, it.finishReason, fallback.name) }

        throw StatusException(
            Status.UNAVAILABLE.withDescription(
                "Both AI providers failed: primary(${primary.name})=${primaryAttempt.exceptionOrNull()?.message}, " +
                    "fallback(${fallback.name})=${fallbackAttempt.exceptionOrNull()?.message}",
            ),
        )
    }
}
