package com.hardcode.proxy.routing

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

/**
 * Where a (re)connecting player lands: normally the game server, but Limbo while a reroll
 * is in flight (the game server is mid-restart during that window and would just refuse the
 * connection) - see the plan's Phase 2/3 notes on Limbo↔Game player migration. Players who
 * specifically need to be pulled into Limbo/back into a fresh run because they voted yes are
 * handled separately by [RerollRoutingListener], since that's a proxy-initiated move on an
 * *already connected* player, not an initial-connection choice.
 */
class InitialServerRouter(
    private val proxyServer: ProxyServer,
    private val routingState: RunRoutingState,
    private val logger: Logger,
) {
    @Subscribe
    fun onChooseInitialServer(event: PlayerChooseInitialServerEvent) {
        val targetName = if (routingState.pendingRerollRunId != null) "limbo" else "game"
        val target = proxyServer.getServer(targetName).orElse(null)
        if (target == null) {
            logger.warn("No '{}' server registered - falling back to Velocity's own default", targetName)
            return
        }
        event.setInitialServer(target)
    }
}
