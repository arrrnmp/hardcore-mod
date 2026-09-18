package com.hardcode.limbo

import com.hardcode.common.redis.RedisConnection
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.limbo.status.RerollStatusBroadcaster
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory

/**
 * Entry point for the dedicated Limbo server mod (Phase 3). This server's own
 * `minecraft:overworld` is overridden (see `data/minecraft/dimension/overworld.json` and
 * `dimension_type/overworld.json`) into a small void platform with an End-like sky and
 * `has_ender_dragon_fight: false` - there is no Ender Dragon here, and none of the vanilla
 * End's dragon-fight machinery ever runs since we never touch the real `minecraft:the_end`
 * dimension at all.
 *
 * Every player who connects to this server is, by definition, in Limbo: adventure mode,
 * permanent flight, invulnerable, with a Y<0 safety net (see [LimboPlayerSetup],
 * [VoidSafetyNet]). Actually moving players in and out of Limbo as part of a real run's
 * vote/re-roll flow is the proxy's job and isn't wired up yet - see the project plan's
 * Phase 2 note on Limbo↔Game migration.
 */
object HardcoreLimboMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-limbo")

    private var redis: RedisEventBus? = null

    override fun onInitialize() {
        logger.info("Hardcore Limbo Server mod initializing")

        redis = runCatching { RedisEventBus(RedisConnection.fromEnv()) }
            .onFailure { logger.warn("Could not set up Redis client, reroll status broadcasts will be disabled: {}", it.message) }
            .getOrNull()

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            LimboPlayerSetup.apply(handler.player)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            VoidSafetyNet.tick(server)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            redis?.let { RerollStatusBroadcaster(server, it).start() }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            redis?.close()
            redis = null
        }
    }
}
