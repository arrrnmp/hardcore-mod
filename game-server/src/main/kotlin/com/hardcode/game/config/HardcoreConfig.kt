package com.hardcode.game.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/** Operator-tunable settings, persisted so they survive restarts. */
@Serializable
data class HardcoreConfig(val voteDurationSeconds: Int = 60)

/** Loads/saves [HardcoreConfig] and hot-reloads it for anything reading [config] live. */
class ConfigManager(private val path: Path) {
    private val logger = LoggerFactory.getLogger("hardcore-game")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    var config: HardcoreConfig = load()
        private set

    fun update(newConfig: HardcoreConfig) {
        config = newConfig
        save(newConfig)
    }

    private fun load(): HardcoreConfig {
        if (!Files.exists(path)) return HardcoreConfig().also(::save)
        return runCatching { json.decodeFromString<HardcoreConfig>(Files.readString(path)) }
            .onFailure { logger.warn("Failed to read {}, using defaults: {}", path, it.message) }
            .getOrElse { HardcoreConfig() }
    }

    private fun save(value: HardcoreConfig) {
        runCatching {
            Files.createDirectories(path.parent)
            Files.writeString(path, json.encodeToString(value))
        }.onFailure { logger.warn("Failed to write {}: {}", path, it.message) }
    }
}
