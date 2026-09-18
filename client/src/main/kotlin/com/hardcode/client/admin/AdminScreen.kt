package com.hardcode.client.admin

import com.hardcode.common.model.AdminAction
import com.hardcode.common.model.AdminActionType
import com.hardcode.common.model.AdminSnapshot
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import kotlinx.serialization.json.Json

/**
 * The in-game admin panel: a real custom-rendered [Screen] (own layout, no chest-inventory
 * texture reuse), gated server-side on operator status for every action - this screen only
 * ever exists because the server already checked that before sending the first snapshot
 * (see AdminService.isAuthorized / the `/hardcore admin` command).
 *
 * Deliberately simple widget-wise for Phase 5 (buttons + drawn text, no scroll widgets) -
 * the roster/leaderboard lists are short enough in practice that this is plenty readable.
 */
class AdminScreen(private var snapshot: AdminSnapshot) : Screen(Component.literal("Hardcore Admin")) {
    private enum class Tab(val label: String) {
        RUN_CONTROL("Run Control"),
        PLAYERS("Players"),
        HALL_OF_SHAME("Hall of Shame"),
        CONFIG("Config"),
    }

    private var tab: Tab = Tab.RUN_CONTROL

    companion object {
        private const val PANEL_BG_ARGB = 0xE0101018.toInt()
        private const val HEADER_BG_ARGB = 0xF0201028.toInt()
        private const val TOP = 20
        private const val LEFT = 20
        private const val ROW_HEIGHT = 22
    }

    fun updateSnapshot(newSnapshot: AdminSnapshot) {
        snapshot = newSnapshot
        rebuild()
    }

    override fun init() {
        rebuild()
    }

    private fun rebuild() {
        clearWidgets()

        var x = LEFT
        for (candidate in Tab.entries) {
            addRenderableWidget(
                Button.builder(Component.literal(candidate.label)) { switchTab(candidate) }
                    .bounds(x, TOP, 110, 20)
                    .build(),
            )
            x += 114
        }

        when (tab) {
            Tab.RUN_CONTROL -> initRunControl()
            Tab.PLAYERS -> initPlayers()
            Tab.HALL_OF_SHAME -> {}
            Tab.CONFIG -> initConfig()
        }
    }

    private fun switchTab(newTab: Tab) {
        tab = newTab
        rebuild()
    }

    private fun initRunControl() {
        var y = TOP + 40
        addRenderableWidget(
            Button.builder(Component.literal("Force Vote")) {
                sendAction(AdminAction(AdminActionType.FORCE_VOTE.name))
            }.bounds(LEFT, y, 150, 20).build(),
        )
        y += ROW_HEIGHT

        val freezeLabel = if (snapshot.frozen) "Force Unfreeze" else "Force Freeze"
        addRenderableWidget(
            Button.builder(Component.literal(freezeLabel)) {
                val type = if (snapshot.frozen) AdminActionType.FORCE_UNFREEZE else AdminActionType.FORCE_FREEZE
                sendAction(AdminAction(type.name))
            }.bounds(LEFT, y, 150, 20).build(),
        )
        y += ROW_HEIGHT

        addRenderableWidget(
            Button.builder(Component.literal("Force Re-roll")) {
                sendAction(AdminAction(AdminActionType.FORCE_REROLL.name))
            }.bounds(LEFT, y, 150, 20).build(),
        )
    }

    private fun initPlayers() {
        var y = TOP + 40
        for (entry in snapshot.roster) {
            addRenderableWidget(
                Button.builder(Component.literal("Kick")) {
                    sendAction(AdminAction(AdminActionType.KICK_PLAYER.name, targetUuid = entry.uuid))
                }.bounds(LEFT + 280, y, 60, 18).build(),
            )
            y += ROW_HEIGHT
        }
    }

    private fun initConfig() {
        val y = TOP + 40
        addRenderableWidget(
            Button.builder(Component.literal("-10s")) {
                sendAction(
                    AdminAction(
                        AdminActionType.UPDATE_CONFIG.name,
                        voteDurationSeconds = snapshot.voteDurationSeconds - 10,
                    ),
                )
            }.bounds(LEFT, y, 60, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("+10s")) {
                sendAction(
                    AdminAction(
                        AdminActionType.UPDATE_CONFIG.name,
                        voteDurationSeconds = snapshot.voteDurationSeconds + 10,
                    ),
                )
            }.bounds(LEFT + 160, y, 60, 20).build(),
        )
    }

    private fun sendAction(action: AdminAction) {
        ClientPlayNetworking.send(AdminActionPayload(Json.encodeToString(action)))
    }

    override fun extractRenderState(graphics: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), PANEL_BG_ARGB)
        graphics.fill(0, 0, graphics.guiWidth(), TOP + 40, HEADER_BG_ARGB)
        graphics.text(font, "Hardcore Admin - ${tab.label}", LEFT, 6, CommonColors.WHITE)

        var y = TOP + 40
        when (tab) {
            Tab.RUN_CONTROL -> {
                graphics.text(font, "Run: ${snapshot.runId.take(8)}", LEFT + 170, y, CommonColors.LIGHT_GRAY)
                graphics.text(font, "State: ${snapshot.state}", LEFT + 170, y + 12, CommonColors.LIGHT_GRAY)
                graphics.text(
                    font,
                    if (snapshot.frozen) "FROZEN - waiting for ${snapshot.waitingForPlayerName}" else "Not frozen",
                    LEFT + 170,
                    y + 24,
                    if (snapshot.frozen) CommonColors.RED else CommonColors.GREEN,
                )
            }
            Tab.PLAYERS -> {
                for (entry in snapshot.roster) {
                    val status = buildString {
                        append(if (entry.online) "online" else "offline")
                        if (entry.dead) append(", dead")
                    }
                    graphics.text(font, "${entry.name} ($status)", LEFT, y + 5, CommonColors.WHITE)
                    y += ROW_HEIGHT
                }
                if (snapshot.roster.isEmpty()) {
                    graphics.text(font, "No one has joined this run yet.", LEFT, y + 5, CommonColors.LIGHT_GRAY)
                }
            }
            Tab.HALL_OF_SHAME -> {
                if (snapshot.leaderboard.isEmpty()) {
                    graphics.text(font, "No deaths recorded yet.", LEFT, y + 5, CommonColors.LIGHT_GRAY)
                }
                for ((index, entry) in snapshot.leaderboard.withIndex()) {
                    graphics.text(font, "${index + 1}. ${entry.name} - ${entry.deaths} deaths", LEFT, y + 5, CommonColors.WHITE)
                    y += ROW_HEIGHT
                }
            }
            Tab.CONFIG -> {
                graphics.text(font, "Vote duration: ${snapshot.voteDurationSeconds}s", LEFT + 70, y + 5, CommonColors.WHITE)
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
