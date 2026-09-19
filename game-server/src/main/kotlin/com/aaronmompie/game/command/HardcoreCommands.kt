package com.aaronmompie.game.command

import com.aaronmompie.common.model.LeaderboardEntry
import com.aaronmompie.common.model.VoteChoice
import com.aaronmompie.game.HardcoreGameMod
import com.aaronmompie.game.storage.HallOfShamePayload
import com.mojang.brigadier.CommandDispatcher
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/run vote <yes|no>`, `/run status` and `/hardcore admin` (opens the admin panel).
 *
 * Reads [HardcoreGameMod.runManager]/[HardcoreGameMod.voteManager]/
 * [HardcoreGameMod.adminService] inside each executor lambda (not at [register] time):
 * CommandRegistrationCallback can fire before ServerLifecycleEvents.SERVER_STARTING has
 * initialized those lateinit vars, but a player can never run a command before the server
 * has fully started, so resolving them lazily at execution time is always safe.
 */
object HardcoreCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("run")
                .then(
                    Commands.literal("vote")
                        .then(Commands.literal("yes").executes { ctx -> castVote(ctx.source, VoteChoice.YES) })
                        .then(Commands.literal("no").executes { ctx -> castVote(ctx.source, VoteChoice.NO) }),
                )
                .then(Commands.literal("status").executes { ctx -> sendStatus(ctx.source) })
                .then(Commands.literal("halloffame").executes { ctx -> sendHallOfShame(ctx.source) }),
        )

        dispatcher.register(
            Commands.literal("hardcore")
                .then(Commands.literal("admin").executes { ctx -> openAdminPanel(ctx.source) }),
        )
    }

    private fun castVote(source: CommandSourceStack, choice: VoteChoice): Int {
        val player = source.playerOrException
        val voteManager = HardcoreGameMod.voteManager
        if (!voteManager.isActive) {
            source.sendFailure(Component.literal("There's no vote in progress right now."))
            return 0
        }
        if (!voteManager.castVote(player.getUUID(), choice)) {
            source.sendFailure(Component.literal("You're not eligible to vote in this run."))
            return 0
        }
        return 1
    }

    private fun sendStatus(source: CommandSourceStack): Int {
        val runManager = HardcoreGameMod.runManager
        val voteManager = HardcoreGameMod.voteManager
        source.sendSuccess(
            {
                Component.literal(
                    "Run ${runManager.runId.take(8)} - state: ${runManager.state} - vote active: ${voteManager.isActive}",
                )
            },
            false,
        )
        return 1
    }

    /** Available to anyone, unlike the admin panel - see the project plan section 10. */
    private fun sendHallOfShame(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val entries = HardcoreGameMod.hallOfShame.leaderboard().map { LeaderboardEntry(it.playerName, it.deaths) }
        ServerPlayNetworking.send(player, HallOfShamePayload(Json.encodeToString(entries)))
        return 1
    }

    private fun openAdminPanel(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val adminService = HardcoreGameMod.adminService
        if (!adminService.isAuthorized(player)) {
            source.sendFailure(Component.literal("You must be an operator to use the admin panel."))
            return 0
        }
        adminService.sendSnapshot(player)
        return 1
    }
}
