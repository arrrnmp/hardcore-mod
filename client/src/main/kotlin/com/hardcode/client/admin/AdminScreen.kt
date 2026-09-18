package com.hardcode.client.admin

import com.hardcode.client.ui.LeaderboardRenderer
import com.hardcode.client.ui.PanelButton
import com.hardcode.common.model.AdminAction
import com.hardcode.common.model.AdminActionType
import com.hardcode.common.model.AdminSnapshot
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors

/**
 * The in-game admin panel: a real custom-rendered [Screen] with its own widgets
 * ([PanelButton], not vanilla button sprites) - gated server-side on operator status for
 * every action, this screen only ever exists because the server already checked that
 * before sending the first snapshot (see AdminService.isAuthorized / the `/hardcore admin`
 * command).
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
        private const val BG_TOP_ARGB = 0xF0140A20.toInt()
        private const val BG_BOTTOM_ARGB = 0xF00A0614.toInt()
        private const val HEADER_ACCENT = 0xFF8A5CE0.toInt()
        private const val CARD_BG = 0x40FFFFFF
        private const val TOP = 34
        private const val LEFT = 20
        private const val ROW_HEIGHT = 24
        private const val TAB_WIDTH = 118
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
                PanelButton(x, TOP, TAB_WIDTH, 22, Component.literal(candidate.label), highlighted = candidate == tab) {
                    switchTab(candidate)
                },
            )
            x += TAB_WIDTH + 4
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
        var y = TOP + 50
        addRenderableWidget(
            PanelButton(LEFT, y, 160, 22, Component.literal("Force Vote")) {
                sendAction(AdminAction(AdminActionType.FORCE_VOTE.name))
            },
        )
        y += ROW_HEIGHT

        val freezeLabel = if (snapshot.frozen) "Force Unfreeze" else "Force Freeze"
        addRenderableWidget(
            PanelButton(LEFT, y, 160, 22, Component.literal(freezeLabel)) {
                val type = if (snapshot.frozen) AdminActionType.FORCE_UNFREEZE else AdminActionType.FORCE_FREEZE
                sendAction(AdminAction(type.name))
            },
        )
        y += ROW_HEIGHT

        addRenderableWidget(
            PanelButton(LEFT, y, 160, 22, Component.literal("Force Re-roll")) {
                sendAction(AdminAction(AdminActionType.FORCE_REROLL.name))
            },
        )
    }

    private fun initPlayers() {
        var y = TOP + 50
        for (entry in snapshot.roster) {
            addRenderableWidget(
                PanelButton(LEFT + 300, y, 64, 20, Component.literal("Kick")) {
                    sendAction(AdminAction(AdminActionType.KICK_PLAYER.name, targetUuid = entry.uuid))
                },
            )
            y += ROW_HEIGHT
        }
    }

    private fun initConfig() {
        val y = TOP + 50
        addRenderableWidget(
            PanelButton(LEFT, y, 50, 22, Component.literal("-10s")) {
                sendAction(
                    AdminAction(
                        AdminActionType.UPDATE_CONFIG.name,
                        voteDurationSeconds = snapshot.voteDurationSeconds - 10,
                    ),
                )
            },
        )
        addRenderableWidget(
            PanelButton(LEFT + 190, y, 50, 22, Component.literal("+10s")) {
                sendAction(
                    AdminAction(
                        AdminActionType.UPDATE_CONFIG.name,
                        voteDurationSeconds = snapshot.voteDurationSeconds + 10,
                    ),
                )
            },
        )
    }

    private fun sendAction(action: AdminAction) {
        ClientPlayNetworking.send(AdminActionPayload(Json.encodeToString(action)))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fillGradient(0, 0, graphics.guiWidth(), graphics.guiHeight(), BG_TOP_ARGB, BG_BOTTOM_ARGB)
        graphics.fill(0, 0, graphics.guiWidth(), 28, HEADER_ACCENT)
        graphics.text(font, "HARDCORE ADMIN", LEFT, 10, CommonColors.WHITE)

        val y0 = TOP + 50
        when (tab) {
            Tab.RUN_CONTROL -> {
                val cardX = LEFT + 180
                graphics.fill(cardX, y0, cardX + 220, y0 + 70, CARD_BG)
                graphics.text(font, "Run ${snapshot.runId.take(8)}", cardX + 8, y0 + 8, CommonColors.LIGHT_GRAY)
                graphics.text(font, "State: ${snapshot.state}", cardX + 8, y0 + 22, CommonColors.LIGHT_GRAY)
                graphics.text(
                    font,
                    if (snapshot.frozen) "FROZEN - waiting for ${snapshot.waitingForPlayerName}" else "Not frozen",
                    cardX + 8,
                    y0 + 40,
                    if (snapshot.frozen) CommonColors.SOFT_RED else CommonColors.GREEN,
                )
            }
            Tab.PLAYERS -> {
                var y = y0
                for ((index, entry) in snapshot.roster.withIndex()) {
                    if (index % 2 == 0) graphics.fill(LEFT, y - 2, LEFT + 360, y + 18, CARD_BG)
                    val statusColor = if (entry.dead) CommonColors.SOFT_RED else if (entry.online) CommonColors.GREEN else CommonColors.LIGHT_GRAY
                    val status = buildString {
                        append(if (entry.online) "online" else "offline")
                        if (entry.dead) append(", dead")
                    }
                    graphics.text(font, entry.name, LEFT + 6, y + 2, CommonColors.WHITE)
                    graphics.text(font, status, LEFT + 150, y + 2, statusColor)
                    y += ROW_HEIGHT
                }
                if (snapshot.roster.isEmpty()) {
                    graphics.text(font, "No one has joined this run yet.", LEFT, y0 + 2, CommonColors.LIGHT_GRAY)
                }
            }
            Tab.HALL_OF_SHAME -> {
                graphics.fill(LEFT, y0, LEFT + 360, y0 + 20 + snapshot.leaderboard.size * 18, CARD_BG)
                LeaderboardRenderer.render(graphics, font, snapshot.leaderboard, LEFT, y0 + 2, 360)
            }
            Tab.CONFIG -> {
                graphics.text(font, "Vote duration: ${snapshot.voteDurationSeconds}s", LEFT + 60, y0 + 6, CommonColors.WHITE)
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }
}
