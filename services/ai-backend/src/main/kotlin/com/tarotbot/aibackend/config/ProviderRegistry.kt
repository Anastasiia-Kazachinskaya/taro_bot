package com.tarotbot.aibackend.config

import com.charleskorn.kaml.Yaml
import com.tarotbot.aibackend.provider.AiProvider
import com.tarotbot.aibackend.provider.createProvider
import com.tarotbot.config.YamlConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the runtime-swappable primary/fallback provider pair. Reloads
 * automatically when `providers.yaml` changes on disk (so a hand-edit or a
 * sibling replica's [setActive] call is picked up without a restart), and
 * [setActive] additionally rewrites the file so other replicas' watchers
 * see the change too (best-effort — a failed write only affects propagation,
 * the in-memory swap on this instance still takes effect).
 */
class ProviderRegistry(
    private val configPath: Path,
    private val engine: HttpClientEngine = CIO.create(),
    private val env: Map<String, String> = System.getenv(),
    startWatcher: Boolean = true,
) {
    private val configRef = AtomicReference<ProvidersConfig>()
    private val providersRef = AtomicReference<Pair<AiProvider, AiProvider>>()
    private var watcherThread: Thread? = null

    init {
        ensureConfigFileExists()
        applyConfig(YamlConfig.load(configPath, ProvidersConfig.serializer()))
        if (startWatcher) startWatching()
    }

    /** The current (primary, fallback) provider pair. */
    fun current(): Pair<AiProvider, AiProvider> = providersRef.get()

    /** The current (primary, fallback) provider ids. */
    fun status(): Pair<String, String> = configRef.get().let { it.primary to it.fallback }

    /** Swaps the active primary/fallback ids; both must already exist in `providers`. */
    fun setActive(primary: String, fallback: String) {
        val cfg = configRef.get()
        require(primary in cfg.providers) { "Unknown provider id: '$primary'" }
        require(fallback in cfg.providers) { "Unknown provider id: '$fallback'" }
        val updated = cfg.copy(primary = primary, fallback = fallback)
        applyConfig(updated)
        runCatching {
            Files.writeString(configPath, Yaml.default.encodeToString(ProvidersConfig.serializer(), updated))
        }.onFailure {
            println("Warning: failed to persist provider config to $configPath: ${it.message}")
        }
    }

    fun shutdown() {
        watcherThread?.interrupt()
    }

    private fun ensureConfigFileExists() {
        if (Files.exists(configPath)) return
        Files.createDirectories(configPath.parent)
        val default = javaClass.getResourceAsStream("/providers.default.yaml")
            ?: error("Bundled default providers.default.yaml not found on classpath")
        default.use { Files.copy(it, configPath) }
    }

    private fun applyConfig(cfg: ProvidersConfig) {
        val primaryEntry = cfg.providers.getValue(cfg.primary)
        val fallbackEntry = cfg.providers.getValue(cfg.fallback)
        val primary = createProvider(cfg.primary, primaryEntry, env, engine)
        val fallback = createProvider(cfg.fallback, fallbackEntry, env, engine)
        configRef.set(cfg)
        providersRef.set(primary to fallback)
    }

    private fun startWatching() {
        val watchService = configPath.fileSystem.newWatchService()
        configPath.parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
        val thread = Thread({
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val key = watchService.take()
                    val relevant = key.pollEvents().any { event ->
                        (event.context() as? Path)?.toString() == configPath.fileName.toString()
                    }
                    key.reset()
                    if (relevant) {
                        Thread.sleep(1000) // debounce a half-written file
                        reload()
                    }
                }
            } catch (_: InterruptedException) {
                // shutdown requested
            }
        }, "provider-config-watcher")
        thread.isDaemon = true
        thread.start()
        watcherThread = thread
    }

    private fun reload() {
        runCatching { YamlConfig.load(configPath, ProvidersConfig.serializer()) }
            .onSuccess {
                applyConfig(it)
                println("Reloaded provider config from $configPath: primary=${it.primary} fallback=${it.fallback}")
            }
            .onFailure {
                println("Warning: failed to reload provider config from $configPath, keeping previous: ${it.message}")
            }
    }
}
