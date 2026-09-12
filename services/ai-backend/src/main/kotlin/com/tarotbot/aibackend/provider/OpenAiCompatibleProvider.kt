package com.tarotbot.aibackend.provider

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
private data class ChatMessageDto(val role: String, val content: String)

@Serializable
private data class ChatCompletionRequestDto(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double = 0.7,
    @SerialName("presence_penalty") val presencePenalty: Int = 0,
    @SerialName("top_p") val topP: Int = 1,
    val messages: List<ChatMessageDto>,
)

@Serializable
private data class ChatChoiceDto(
    val message: ChatMessageDto,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class ChatCompletionResponseDto(val choices: List<ChatChoiceDto> = emptyList())

/**
 * Shared HTTP client logic for any OpenAI-chat-completions-compatible provider
 * (DeepSeek and GigaChat/Cloud.ru both speak this shape). Retries mirror the old
 * bot's `RETRYABLE_ERRORS`: connection failures, timeouts, 5xx, and 429 — up to
 * 3 total attempts with backoff starting around 1s and roughly doubling.
 */
open class OpenAiCompatibleProvider(
    override val name: String,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    engine: HttpClientEngine,
) : AiProvider {

    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 60_000 }
        install(HttpRequestRetry) {
            maxRetries = 2
            retryIf { _, response -> response.status.value == 429 || response.status.value >= 500 }
            retryOnExceptionIf { _, cause -> cause is IOException }
            exponentialDelay()
        }
    }

    override suspend fun chat(systemPrompt: String, userPrompt: String, maxTokens: Int): ChatResult {
        val response: ChatCompletionResponseDto = client.post("$baseUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(
                ChatCompletionRequestDto(
                    model = model,
                    maxTokens = maxTokens,
                    messages = listOf(
                        ChatMessageDto("system", systemPrompt),
                        ChatMessageDto("user", userPrompt),
                    ),
                ),
            )
        }.body()

        val choice = response.choices.firstOrNull()
            ?: throw IllegalStateException("$name returned no choices")
        val text = choice.message.content
        check(text.isNotBlank()) { "$name returned an empty response" }
        return ChatResult(text, choice.finishReason ?: "stop")
    }
}
