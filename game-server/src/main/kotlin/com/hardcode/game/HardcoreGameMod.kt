package com.hardcode.game

import com.hardcode.common.model.RunState
import com.hardcode.common.redis.RedisConnection
import com.hardcode.common.redis.RedisEvent
import com.hardcode.common.redis.RedisEventBus
import com.hardcode.common.redis.RedisSchema
import com.hardcode.common.storage.Database
import com.hardcode.game.admin.AdminActionPayload
import com.hardcode.game.admin.AdminService
import com.hardcode.game.admin.AdminSnapshotPayload
import com.hardcode.game.command.HardcoreCommands
import com.hardcode.game.config.ConfigManager
import com.hardcode.game.death.DeathFlashPayload
import com.hardcode.game.death.DeathHandler
import com.hardcode.game.freeze.FreezeManager
import com.hardcode.game.freeze.FreezeStatePayload
import com.hardcode.game.run.RerollCoordinator
import com.hardcode.game.run.RunManager
import com.hardcode.game.run.VoteManager
import com.hardcode.game.stats.StatsBroadcaster
import com.hardcode.game.stats.StatsSnapshotPayload
import com.hardcode.game.storage.HallOfShame
import com.hardcode.game.storage.HallOfShamePayload
import com.hardcode.game.tablist.HealthTabList
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.level.GameType
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Entry point for the game-server mod. Phase 5: adds the in-game admin panel on top of
 * Phase 4's freeze system - run control / player management / Hall of Shame / config, all
 * gated on operator status re-checked server-side for every action (see [AdminService]).
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

    lateinit var adminService: AdminService
        private set
    lateinit var hallOfShame: HallOfShame
        private set

    private lateinit var freezeManager: FreezeManager
    private lateinit var statsBroadcaster: StatsBroadcaster
    private var database: Database? = null
    private var redis: RedisEventBus? = null

    override fun onInitialize() {
        logger.info("Hardcore Game Server mod initializing")

        PayloadTypeRegistry.clientboundPlay().register(FreezeStatePayload.TYPE, FreezeStatePayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(AdminSnapshotPayload.TYPE, AdminSnapshotPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(AdminActionPayload.TYPE, AdminActionPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(DeathFlashPayload.TYPE, DeathFlashPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.CODEC)
        PayloadTypeRegistry.clientboundPlay().register(HallOfShamePayload.TYPE, HallOfShamePayload.CODEC)

        redis = runCatching { RedisEventBus(RedisConnection.fromEnv()) }
            .onFailure { logger.warn("Could not set up Redis client, reroll hand-off will be disabled: {}", it.message) }
            .getOrNull()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dataDir = FabricLoader.getInstance().gameDir.resolve("hardcore")
            Files.createDirectories(dataDir)

            val db = Database(dataDir.resolve("hardcore.db"))
            database = db
            hallOfShame = HallOfShame(db)
            val configManager = ConfigManager(dataDir.resolve("config.json"))

            runManager = RunManager()
            logger.info("Starting run {}", runManager.runId)

            val rerollCoordinator = RerollCoordinator(server, runManager, redis)
            voteManager = VoteManager(server, runManager, rerollCoordinator, configManager)
            DeathHandler(server, runManager, voteManager, hallOfShame, configManager).register()
            freezeManager = FreezeManager(server, runManager, redis)
            freezeManager.registerEnforcement()
            statsBroadcaster = StatsBroadcaster(server, runManager)
            adminService = AdminService(
                server,
                runManager,
                voteManager,
                freezeManager,
                rerollCoordinator,
                hallOfShame,
                configManager,
            )
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

            // The run is over the moment someone's died, regardless of vote outcome - a
            // late joiner (or a "no" voter who never left) must not get to play survival on
            // a run that's already decided it's done.
            if (runManager.state == RunState.VOTE_PENDING || runManager.state == RunState.RUN_ENDED) {
                player.setGameMode(GameType.SPECTATOR)
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            freezeManager.onDisconnect(player.getUUID(), player.getGameProfile().name)
        }

        ServerPlayNetworking.registerGlobalReceiver(AdminActionPayload.TYPE) { payload, context ->
            adminService.handleAction(context.player(), payload)
        }

        ServerTickEvents.END_SERVER_TICK.register {
            if (::voteManager.isInitialized) voteManager.tick()
            if (::statsBroadcaster.isInitialized) statsBroadcaster.tick()
            if (::freezeManager.isInitialized) freezeManager.tick()
        }

        // This can fire before SERVER_STARTING above, so HardcoreCommands must resolve
        // runManager/voteManager/adminService itself at command-execution time, not receive
        // them here.
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
