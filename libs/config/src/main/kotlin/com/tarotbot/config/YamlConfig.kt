package com.tarotbot.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import java.nio.file.Files
import java.nio.file.Path

/**
 * Minimal YAML config loader shared by services that need runtime-reloadable
 * config files (e.g. the AI backend's provider config). The document shape
 * itself is defined by each caller's own `@Serializable` data class.
 */
object YamlConfig {

    private val yaml = Yaml.default

    fun <T> load(path: Path, deserializer: DeserializationStrategy<T>): T {
        val text = Files.readString(path)
        return yaml.decodeFromString(deserializer, text)
    }
}
