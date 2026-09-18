package com.hardcode.game

import com.hardcode.common.storage.Database
import com.hardcode.game.command.HardcoreCommands
import com.hardcode.game.death.DeathHandler
import com.hardcode.game.run.RunManager
import com.hardcode.game.run.VoteManager
import com.hardcode.game.storage.HallOfShame
import com.hardcode.game.tablist.HealthTabList
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Entry point for the game-server mod. Phase 1: core run-lifecycle MVP - roster tracking,
 * death → mass spectator, chat-based vote, Hall of Shame writes, tab-list hearts.
 *
 * World re-roll (Phase 2), the dedicated Limbo server hookup (Phase 3), freeze-on-disconnect
 * (Phase 4) and the admin panel (Phase 5) are not implemented yet - see the project plan.
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
    private var database: Database? = null

    override fun onInitialize() {
        logger.info("Hardcore Game Server mod initializing")

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            val dataDir = FabricLoader.getInstance().gameDir.resolve("hardcore")
            Files.createDirectories(dataDir)

            val db = Database(dataDir.resolve("hardcore.db"))
            database = db
            hallOfShame = HallOfShame(db)

            runManager = RunManager()
            hallOfShame.ensureRun(runManager.runId, runManager.seed, System.currentTimeMillis())
            logger.info("Started run {} (seed {})", runManager.runId, runManager.seed)

            voteManager = VoteManager(server, runManager)
            DeathHandler(server, runManager, voteManager, hallOfShame).register()
        }

        // Not SERVER_STARTING: PlayerList doesn't exist yet at that point (confirmed by
        // running the dev server - it NPEs inside ServerScoreboard.setDisplayObjective,
        // which reaches into getPlayerList().getPlayers()). SERVER_STARTED fires once the
        // server (and PlayerList) is fully up.
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            HealthTabList.install(server)
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
        }
    }
}
