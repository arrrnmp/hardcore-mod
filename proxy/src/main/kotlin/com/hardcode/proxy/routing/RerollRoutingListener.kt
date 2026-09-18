package com.hardcode.proxy.routing

import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Subscribes to the same Redis reroll events run-launcher and game-server use, and actually
 * moves players between the "limbo" and "game" registered servers - the piece that was
 * missing since Phase 2 (see the plan's Phase 2 note on Limbo↔Game player migration).
 */
class RerollRoutingListener(
    private val proxyServer: ProxyServer,
    private val redis: RedisEventBus,
    private val routingState: RunRoutingState,
    private val logger: Logger,
) {
    fun start() {
        thread(isDaemon = true, name = "redis-reroll-requested-listener") {
            runCatching {
                redis.subscribe(RedisSchema.Channels.REROLL_REQUESTED, RedisEvent.RerollRequested.serializer()) { event ->
                    onRerollRequested(event)
                }
            }.onFailure { logger.warn("Reroll-requested subscription ended: {}", it.message) }
        }

        thread(isDaemon = true, name = "redis-reroll-ready-listener") {
            runCatching {
                redis.subscribe(RedisSchema.Channels.REROLL_READY, RedisEvent.RerollReady.serializer()) { event ->
                    onRerollReady(event)
                }
            }.onFailure { logger.warn("Reroll-ready subscription ended: {}", it.message) }
        }
    }

    private fun onRerollRequested(event: RedisEvent.RerollRequested) {
        val yesVoters = event.yesVoterUuids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        routingState.onRerollRequested(event.newRunId, yesVoters)
        logger.info("Reroll requested: {} -> {} ({} yes voters) - moving them to limbo", event.previousRunId, event.newRunId, yesVoters.size)

        val limbo = proxyServer.getServer("limbo").orElse(null)
        if (limbo == null) {
            logger.warn("No 'limbo' server registered - can't move yes voters there")
            return
        }
        for (uuid in yesVoters) {
            proxyServer.getPlayer(uuid).ifPresent { it.createConnectionRequest(limbo).fireAndForget() }
        }
    }

    private fun onRerollReady(event: RedisEvent.RerollReady) {
        val yesVoters = routingState.onRerollReady(event.runId)
        logger.info("Reroll ready: {} - moving {} players into the new run", event.runId, yesVoters.size)

        val game = proxyServer.getServer("game").orElse(null)
        if (game == null) {
            logger.warn("No 'game' server registered - can't move players into the new run")
            return
        }
        for (uuid in yesVoters) {
            proxyServer.getPlayer(uuid).ifPresent { it.createConnectionRequest(game).fireAndForget() }
        }
    }
}
