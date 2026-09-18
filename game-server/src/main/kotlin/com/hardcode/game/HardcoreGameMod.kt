package com.hardcode.game

import com.hardcode.common.redis.RedisConnection
import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import com.hardcode.common.storage.Database
import com.hardcode.game.command.HardcoreCommands
import com.hardcode.game.death.DeathHandler
import com.hardcode.game.freeze.FreezeManager
import com.hardcode.game.freeze.FreezeStatePayload
import com.hardcode.game.run.RerollCoordinator
import com.hardcode.game.run.RunManager
import com.hardcode.game.run.VoteManager
import com.hardcode.game.storage.HallOfShame
import com.hardcode.game.tablist.HealthTabList
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Entry point for the game-server mod. Phase 4: adds the freeze-on-disconnect system on top
 * of Phase 2's reroll pipeline - any roster member disconnecting now halts world simulation
 * (via vanilla's own tick-freeze) until they return, per the project plan section 5.
 *
 * The admin panel (Phase 5) is not implemented yet - see the project plan.
 */
object HardcoreGameMod : ModInitializer {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    // Not private: HardcoreCommands reads these lazily at command-execution time, which is
    // always well after SERVER_STARTING has run - but CommandRegistrationCallback itself can
    // fire before SERVER_STARTING, so nothing may capture these lateinit vars by value at
    // registration time (see the comment on the CommandRegistrationCallback block below).
    lateinit var runManager: RunManager
        private set
    lateinit var voteManager: VoteManager
        private set

    private lateinit var hallOfShame: HallOfShame
    private lateinit var freezeManager: FreezeManager
    private var database: Database? = null
    private var redis: RedisEventBus? = null

    override fun onInitialize() {
        logger.info("Hardcore Game Server mod initializing")

        PayloadTypeRegistry.clientboundPlay().register(FreezeStatePayload.TYPE, FreezeStatePayload.CODEC)

        redis = runCatching { RedisEventBus(RedisConnection.fromEnv()) }
            .onFailure { logger.warn("Could not set up Redis client, reroll hand-off will be disabled: {}", it.message) }
            .getOrNull()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dataDir = FabricLoader.getInstance().gameDir.resolve("hardcore")
            Files.createDirectories(dataDir)

            val db = Database(dataDir.resolve("hardcore.db"))
            database = db
            hallOfShame = HallOfShame(db)

            runManager = RunManager()
            logger.info("Starting run {}", runManager.runId)

            val rerollCoordinator = RerollCoordinator(server, runManager, redis)
            voteManager = VoteManager(server, runManager, rerollCoordinator)
            DeathHandler(server, runManager, voteManager, hallOfShame).register()
            freezeManager = FreezeManager(server, runManager, redis)
        }

        // Not SERVER_STARTING: PlayerList/the overworld don't exist yet at that point
        // (confirmed by running the dev server - HealthTabList.install() NPE'd reaching into
        // getPlayerList().getPlayers()). SERVER_STARTED fires once the server, its world and
        // PlayerList are all fully up.
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            runManager.confirmSeed(server.overworld().getSeed())
            hallOfShame.ensureRun(runManager.runId, runManager.seed, System.currentTimeMillis())
            logger.info("Run {} ready (seed {})", runManager.runId, runManager.seed)

            HealthTabList.install(server)

            redis?.let { bus ->
                runCatching {
                    bus.publish(
                        RedisSchema.Channels.REROLL_READY,
                        RedisEvent.RerollReady.serializer(),
                        RedisEvent.RerollReady(runManager.runId),
                    )
                }.onFailure { logger.warn("Failed to publish reroll-ready: {}", it.message) }
            }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            runManager.ensureJoined(player)
            hallOfShame.ensurePlayerProfile(
                runManager.runId,
                player.getUUID().toString(),
                player.getGameProfile().name,
                System.currentTimeMillis(),
            )
            freezeManager.onReconnect(player.getUUID())
            freezeManager.syncStateTo(player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            freezeManager.onDisconnect(player.getUUID(), player.getGameProfile().name)
        }

        ServerTickEvents.END_SERVER_TICK.register {
            if (::voteManager.isInitialized) voteManager.tick()
        }

        // This can fire before SERVER_STARTING above, so HardcoreCommands must resolve
        // runManager/voteManager itself at command-execution time, not receive them here.
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            HardcoreCommands.register(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register {
            database?.close()
            database = null
            redis?.close()
            redis = null
        }
    }
}
