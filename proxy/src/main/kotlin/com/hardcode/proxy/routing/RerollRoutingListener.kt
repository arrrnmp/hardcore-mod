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

        val limbo = proxyServer.getServer("limbo").orElse(null)
        val game = proxyServer.getServer("game").orElse(null)
        if (limbo == null) {
            logger.warn("No 'limbo' server registered - can't move anyone there")
            return
        }

        // Everyone currently on "game" moves to Limbo, not just yes-voters - the server is
        // about to halt either way (confirmed by real testing: a dead player who couldn't
        // vote, and so was never a yes-voter, got hard-kicked off the whole proxy with
        // nowhere to go once the game server actually stopped). Only yes-voters get
        // automatically carried into the new run once it's ready, in onRerollReady below;
        // everyone else just waits in Limbo.
        val toMove = game?.playersConnected?.map { it.uniqueId }?.toSet() ?: yesVoters
        logger.info(
            "Reroll requested: {} -> {} - moving {} connected players to limbo ({} of them yes-voters)",
            event.previousRunId,
            event.newRunId,
            toMove.size,
            yesVoters.size,
        )
        for (uuid in toMove) {
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
