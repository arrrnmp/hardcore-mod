package com.hardcode.proxy

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

/**
 * Entry point for the Velocity proxy plugin. Phase 0: scaffolding only - proves the
 * toolchain loads on Velocity.
 *
 * Later phases add: routing (re)connecting players to Limbo vs Game based on Redis run
 * state, publishing roster-member-disconnected/reconnected events, and migrating the
 * "Yes" voter roster from Limbo into a freshly re-rolled game server. See the project plan,
 * section 2 and 3.
 */
@Plugin(
    id = "hardcore-proxy",
    name = "Hardcore Proxy",
    version = "0.1.0-SNAPSHOT",
    description = "Routes players between the Limbo and Game servers based on run/freeze state.",
)
class HardcoreProxyPlugin @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
) {
    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("Hardcore Proxy initializing (scaffolding phase)")
    }
}
