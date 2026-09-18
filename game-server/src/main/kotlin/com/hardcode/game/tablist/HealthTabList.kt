package com.hardcode.game.tablist

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria

/**
 * Always-on hearts in the tab list, via vanilla's own scoreboard health objective - no need
 * to reimplement this or depend on an external plugin (e.g. TAB) for the baseline. The
 * richer per-player statistics overlay (food/armor/direction) is bespoke client rendering
 * and lands separately in a later phase.
 */
object HealthTabList {
    private const val OBJECTIVE_NAME = "hardcore_health"

    fun install(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        val objective = scoreboard.getObjective(OBJECTIVE_NAME)
            ?: scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.HEALTH,
                Component.literal("Health"),
                ObjectiveCriteria.RenderType.HEARTS,
                false,
                null,
            )
        scoreboard.setDisplayObjective(DisplaySlot.LIST, objective)
    }
}
