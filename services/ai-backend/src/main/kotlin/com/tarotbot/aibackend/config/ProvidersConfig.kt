package com.tarotbot.aibackend.config

import kotlinx.serialization.Serializable

/** One provider's connection details — everything needed to build an [com.tarotbot.aibackend.provider.AiProvider]. */
@Serializable
data class ProviderEntry(
    val baseUrl: String,
    val model: String,
    val apiKeyEnv: String,
    val timeoutMs: Long = 60_000,
)

/** The runtime-swappable primary/fallback pair, loaded from `providers.yaml`. */
@Serializable
data class ProvidersConfig(
    val primary: String,
    val fallback: String,
    val providers: Map<String, ProviderEntry>,
)
