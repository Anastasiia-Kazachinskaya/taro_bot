package com.tarotbot.aibackend.provider

import com.tarotbot.aibackend.config.ProviderEntry
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

class DeepSeekProvider(entry: ProviderEntry, apiKey: String, engine: HttpClientEngine = CIO.create()) :
    OpenAiCompatibleProvider("deepseek", entry.baseUrl, entry.model, apiKey, engine)

class GigaChatProvider(entry: ProviderEntry, apiKey: String, engine: HttpClientEngine = CIO.create()) :
    OpenAiCompatibleProvider("gigachat", entry.baseUrl, entry.model, apiKey, engine)

/** Builds the [AiProvider] for [id], reading its API key from [env] via [ProviderEntry.apiKeyEnv]. */
fun createProvider(
    id: String,
    entry: ProviderEntry,
    env: Map<String, String> = System.getenv(),
    engine: HttpClientEngine = CIO.create(),
): AiProvider {
    val apiKey = env[entry.apiKeyEnv].orEmpty()
    return when (id) {
        "deepseek" -> DeepSeekProvider(entry, apiKey, engine)
        "gigachat" -> GigaChatProvider(entry, apiKey, engine)
        else -> throw IllegalArgumentException("Unknown AI provider id: '$id'")
    }
}
