package com.hardcode.game.run

import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisSchema
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

/**
 * Kicks off a world re-roll after a passed vote: publishes the request to Redis (where
 * run-launcher is listening to actually swap the world folder and restart the JVM - see
 * run-launcher's `RerollWatcher`), then gracefully stops this server so run-launcher can do
 * its job. If Redis isn't reachable, there's no run-launcher to hand off to, so this falls
 * back to just continuing on the current world rather than silently hanging the run.
 */
class RerollCoordinator(
    private val server: MinecraftServer,
    private val runManager: RunManager,
    private val redis: RedisEventBus?,
) {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    fun requestReroll() {
        val newRunId = UUID.randomUUID().toString()
        val newSeed = Random.nextLong()

        val published = redis?.let { bus ->
            runCatching {
                bus.publish(
                    RedisSchema.Channels.REROLL_REQUESTED,
                    RedisEvent.RerollRequested.serializer(),
                    RedisEvent.RerollRequested(runManager.runId, newRunId, newSeed),
                )
            }.onFailure { logger.warn("Failed to publish reroll request: {}", it.message) }.isSuccess
        } ?: false

        if (!published) {
            server.playerList.broadcastSystemMessage(
                Component.literal("Re-roll requested, but no run-launcher is connected - the run continues on this world."),
                false,
            )
            logger.warn("Reroll requested but Redis is unavailable; nothing will act on it")
            return
        }

        logger.info("Reroll requested: {} -> {} (seed {})", runManager.runId, newRunId, newSeed)
        server.playerList.broadcastSystemMessage(
            Component.literal("The world is being re-rolled - see you in the next run!"),
            false,
        )
        server.halt(false)
    }
}
