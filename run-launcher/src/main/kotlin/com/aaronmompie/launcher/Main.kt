package com.aaronmompie.launcher

import com.aaronmompie.common.redis.RedisConnection
import com.aaronmompie.common.redis.RedisEvent
import com.aaronmompie.common.redis.RedisEventBus
import com.aaronmompie.common.redis.RedisSchema
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.system.exitProcess

/**
 * Standalone watchdog process that owns the game server's world-folder lifecycle and JVM
 * restarts for world re-rolls (see the project plan, section 4). Not a Minecraft mod - a
 * plain supervisor process:
 *
 * 1. Launches the game server with a fresh run id.
 * 2. Listens on Redis for `reroll-requested`, published by game-server's RerollCoordinator
 *    right before it gracefully halts itself.
 * 3. Once the (now-halted) process actually exits, archives/deletes the old world folder,
 *    writes the new seed into server.properties, and relaunches with the new run id.
 * 4. Repeats. A process exit with no pending reroll request is treated as an intentional
 *    shutdown (manual stop, crash) and ends the watchdog loop too - Phase 2 doesn't include
 *    crash auto-restart, that's an ops-level concern for later (see the plan's Phase 8).
 */
fun main() {
    val logger = LoggerFactory.getLogger("run-launcher")
    logger.info("Hardcore run-launcher starting")

    val config = LauncherConfig.fromEnv(resolveMcVersion())
    logger.info("Config: serverDir={} launchCommand={}", config.serverDir, config.launchCommand)
    requireServerProfile(config.serverDir)

    val worldFolderManager = WorldFolderManager(config.serverDir, config.worldDirName, config.archiveWorlds)
    val redisBus = RedisEventBus(RedisConnection.fromEnv())

    val pendingReroll = AtomicReference<RedisEvent.RerollRequested?>(null)
    thread(isDaemon = true, name = "redis-reroll-subscriber") {
        runCatching {
            redisBus.subscribe(RedisSchema.Channels.REROLL_REQUESTED, RedisEvent.RerollRequested.serializer()) { event ->
                logger.info("Received reroll request: {} -> {}", event.previousRunId, event.newRunId)
                pendingReroll.set(event)
            }
        }.onFailure { logger.error("Redis subscription failed - reroll requests will never arrive", it) }
    }

    var currentRunId = UUID.randomUUID().toString()
    while (true) {
        pendingReroll.set(null)
        val process = GameServerProcess(config.launchWorkingDir, config.launchCommand)
        process.start(currentRunId)
        process.awaitExit()

        val reroll = pendingReroll.get()
        if (reroll == null) {
            logger.info("Game server exited with no pending reroll request - shutting down run-launcher")
            break
        }

        process.stopAndAwait()
        worldFolderManager.applyReroll(reroll.previousRunId, reroll.newSeed)
        currentRunId = reroll.newRunId
        logger.info("Re-rolled world; relaunching as run {}", currentRunId)
    }

    redisBus.close()
}

/**
 * Which MC version profile to supervise. Explicit configuration always wins (no prompt):
 * a pinned HARDCODE_SERVER_DIR (single-version deployment) or HARDCODE_MC_VERSION.
 * Otherwise, on an interactive console, ask - blank/EOF falls back to the default so
 * scripts and pipes never hang. Headless runs without the env var just take the default.
 */
private fun resolveMcVersion(): String {
    if (System.getenv("HARDCODE_SERVER_DIR") != null) return LauncherConfig.DEFAULT_MC_VERSION
    System.getenv(LauncherConfig.MC_VERSION_ENV)?.let { envVersion ->
        if (envVersion in LauncherConfig.SUPPORTED_MC_VERSIONS) return envVersion
        LoggerFactory.getLogger("run-launcher").warn(
            "Ignoring unsupported {}='{}' (supported: {})",
            LauncherConfig.MC_VERSION_ENV,
            envVersion,
            LauncherConfig.SUPPORTED_MC_VERSIONS.sorted(),
        )
    }
    val console = System.console() ?: return LauncherConfig.DEFAULT_MC_VERSION
    while (true) {
        console.writer().write(
            "Game version ${LauncherConfig.SUPPORTED_MC_VERSIONS.sorted()} [${LauncherConfig.DEFAULT_MC_VERSION}]: ",
        )
        console.writer().flush()
        val line = console.readLine()?.trim()
        if (line.isNullOrEmpty()) return LauncherConfig.DEFAULT_MC_VERSION
        if (line in LauncherConfig.SUPPORTED_MC_VERSIONS) return line
        console.writer().println("Unknown version '$line' - pick one of ${LauncherConfig.SUPPORTED_MC_VERSIONS.sorted()}")
    }
}

/** Fail fast when the resolved profile isn't a real server install - never boot the wrong dir. */
private fun requireServerProfile(serverDir: Path) {
    val logger = LoggerFactory.getLogger("run-launcher")
    if (serverDir.resolve("fabric-server-launch.jar").exists()) return
    val candidates = serverDir.parent?.let { parent ->
        runCatching {
            Files.list(parent).use { stream ->
                stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("game-") }
                    .map { it.fileName.toString() }
                    .sorted()
                    .toList()
            }
        }.getOrNull()
    } ?: emptyList()
    logger.error(
        "No game server install at {} (expected fabric-server-launch.jar there). Installed profiles here: {}",
        serverDir,
        candidates.ifEmpty { "<none>" },
    )
    exitProcess(1)
}
