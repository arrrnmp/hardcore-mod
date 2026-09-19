package com.aaronmompie.limbo.status

import com.aaronmompie.common.redis.RedisEvent
import com.aaronmompie.common.redis.RedisEventBus
import com.aaronmompie.common.redis.RedisSchema
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

/**
 * Shows waiting players in Limbo what the game server is up to during a re-roll, driven by
 * the same Redis events run-launcher and game-server already use (see the project plan,
 * section 4). This is status-only for now - actually migrating players out of Limbo once
 * the new run is ready is the proxy's job and isn't wired up yet (see the plan's Phase 2
 * note on Limbo↔Game migration; that's follow-up work once the proxy does real routing).
 */
class RerollStatusBroadcaster(private val server: MinecraftServer, private val redis: RedisEventBus) {
    private val logger = LoggerFactory.getLogger("hardcore-limbo")

    fun start() {
        thread(isDaemon = true, name = "redis-reroll-status-requested") {
            runCatching {
                redis.subscribe(RedisSchema.Channels.REROLL_REQUESTED, RedisEvent.RerollRequested.serializer()) {
                    server.execute {
                        broadcast("The next run is being prepared - hang tight!")
                    }
                }
            }.onFailure { logger.warn("Reroll-requested subscription ended: {}", it.message) }
        }

        thread(isDaemon = true, name = "redis-reroll-status-ready") {
            runCatching {
                redis.subscribe(RedisSchema.Channels.REROLL_READY, RedisEvent.RerollReady.serializer()) {
                    server.execute {
                        broadcast("The next run is ready! (join-back isn't wired up yet - ask an operator)")
                    }
                }
            }.onFailure { logger.warn("Reroll-ready subscription ended: {}", it.message) }
        }
    }

    private fun broadcast(message: String) {
        server.playerList.broadcastSystemMessage(Component.literal(message), false)
    }
}
