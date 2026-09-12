package com.tarotbot.aibackend.config

import com.charleskorn.kaml.Yaml
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ProvidersConfigTest : FunSpec({

    test("round-trips through YAML") {
        val config = ProvidersConfig(
            primary = "deepseek",
            fallback = "gigachat",
            providers = mapOf(
                "deepseek" to ProviderEntry("https://api.deepseek.com/v1", "deepseek-chat", "DEEPSEEK_API_KEY"),
                "gigachat" to ProviderEntry(
                    "https://foundation-models.api.cloud.ru/v1",
                    "ai-sage/GigaChat3.5-432B-A28B",
                    "CLOUD_API_KEY",
                ),
            ),
        )
        val yaml = Yaml.default.encodeToString(ProvidersConfig.serializer(), config)
        val decoded = Yaml.default.decodeFromString(ProvidersConfig.serializer(), yaml)
        decoded shouldBe config
    }

    test("the bundled default resource parses correctly") {
        val text = javaClass.getResourceAsStream("/providers.default.yaml")!!.use { it.reader().readText() }
        val decoded = Yaml.default.decodeFromString(ProvidersConfig.serializer(), text)
        decoded.primary shouldBe "deepseek"
        decoded.fallback shouldBe "gigachat"
        decoded.providers.keys.sorted().shouldContainExactly(listOf("deepseek", "gigachat"))
    }
})
