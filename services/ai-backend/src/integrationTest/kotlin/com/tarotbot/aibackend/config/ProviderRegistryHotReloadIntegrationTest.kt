package com.tarotbot.aibackend.config

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

private const val CONFIG_TEMPLATE = """
primary: %s
fallback: %s
providers:
  deepseek:
    baseUrl: "https://api.deepseek.com/v1"
    model: "deepseek-chat"
    apiKeyEnv: "TEST_DEEPSEEK_KEY"
  gigachat:
    baseUrl: "https://foundation-models.api.cloud.ru/v1"
    model: "ai-sage/GigaChat3.5-432B-A28B"
    apiKeyEnv: "TEST_GIGACHAT_KEY"
"""

class ProviderRegistryHotReloadIntegrationTest {

    @Test
    fun `reloads config when the file changes on disk`() = runBlocking {
        // The JDK's default WatchService on macOS is polling-based with a 10s
        // default interval; shrink it so the test doesn't need a 10s+ deadline.
        System.setProperty("sun.nio.fs.PollingWatchService.pollInterval", "1")

        val dir = Files.createTempDirectory("ai-backend-hotreload-test")
        val path = dir.resolve("providers.yaml")
        Files.writeString(path, CONFIG_TEMPLATE.format("deepseek", "gigachat"))

        val engine = MockEngine { respondOk("{}") }
        val registry = ProviderRegistry(path, engine = engine, env = emptyMap(), startWatcher = true)
        try {
            assertEquals("deepseek" to "gigachat", registry.status())

            Files.writeString(path, CONFIG_TEMPLATE.format("gigachat", "deepseek"))

            var updated = false
            val deadline = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < deadline) {
                if (registry.status() == ("gigachat" to "deepseek")) {
                    updated = true
                    break
                }
                delay(100)
            }
            assertTrue(updated, "Expected ProviderRegistry to pick up the file change within the deadline")
        } finally {
            registry.shutdown()
        }
    }
}
