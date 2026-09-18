package com.hardcode.launcher

import com.hardcode.common.redis.RedisConnection
import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

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

    val config = LauncherConfig.fromEnv()
    logger.info("Config: serverDir={} launchCommand={}", config.serverDir, config.launchCommand)

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
