package com.hardcode.game.command

import com.hardcode.common.model.VoteChoice
import com.hardcode.game.HardcoreGameMod
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/run vote <yes|no>` and `/run status`. Admin panel commands land in Phase 5.
 *
 * Reads [HardcoreGameMod.runManager]/[HardcoreGameMod.voteManager] inside each executor
 * lambda (not at [register] time): CommandRegistrationCallback can fire before
 * ServerLifecycleEvents.SERVER_STARTING has initialized those lateinit vars, but a player
 * can never run a command before the server has fully started, so resolving them lazily at
 * execution time is always safe.
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
                .then(Commands.literal("status").executes { ctx -> sendStatus(ctx.source) }),
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
}
