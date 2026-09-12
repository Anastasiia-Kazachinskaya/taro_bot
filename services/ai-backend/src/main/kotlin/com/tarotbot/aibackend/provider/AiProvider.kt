package com.tarotbot.aibackend.provider

/** Result of one chat-completion call: the text plus why the model stopped. */
data class ChatResult(val text: String, val finishReason: String)

/** One OpenAI-chat-completions-compatible interpretation backend. */
interface AiProvider {
    val name: String
    suspend fun chat(systemPrompt: String, userPrompt: String, maxTokens: Int): ChatResult
}
