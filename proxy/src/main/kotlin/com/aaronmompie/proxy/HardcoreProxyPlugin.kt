package com.aaronmompie.proxy

import com.google.inject.Inject
import com.aaronmompie.common.redis.RedisConnection
import com.aaronmompie.common.redis.RedisEventBus
import com.aaronmompie.proxy.command.ServerSwitchCommand
import com.aaronmompie.proxy.routing.InitialServerRouter
import com.aaronmompie.proxy.routing.RerollRoutingListener
import com.aaronmompie.proxy.routing.RunRoutingState
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

/**
 * Entry point for the Velocity proxy plugin. Closes the gap flagged since Phase 2/3: this
 * now actually routes players between the "game" and "limbo" registered servers (names
 * expected in velocity.toml) based on the same Redis reroll events run-launcher and
 * game-server use - see [RunRoutingState], [RerollRoutingListener], [InitialServerRouter].
 *
 * The `@Plugin` annotation below is kept for documentation but does nothing at runtime:
 * Velocity 4.x discovers plugins via a `velocity-plugin.json` resource that its annotation
 * processor normally generates from this exact annotation - but that processor only runs
 * during Java compilation, and this module has none (it's all Kotlin, `compileJava` is
 * always NO-SOURCE). So `velocity-plugin.json` is hand-authored in
 * src/main/resources/velocity-plugin.json instead - keep both in sync by hand.
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
    private var redis: RedisEventBus? = null

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        logger.info("Hardcore Proxy initializing")

        val routingState = RunRoutingState()
        server.eventManager.register(this, InitialServerRouter(server, routingState, logger))

        val serverCommandMeta = server.commandManager.metaBuilder("server").build()
        server.commandManager.register(serverCommandMeta, ServerSwitchCommand(server))

        redis = runCatching { RedisEventBus(RedisConnection.fromEnv()) }
            .onFailure { logger.warn("Could not set up Redis client, reroll routing will be disabled: {}", it.message) }
            .getOrNull()

        redis?.let { RerollRoutingListener(server, it, routingState, logger).start() }
    }
}
