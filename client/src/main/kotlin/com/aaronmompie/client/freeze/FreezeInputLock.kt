package com.aaronmompie.client.freeze

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.player.ClientInput

/**
 * While [FreezeClientState.frozen], stops the local player from moving, jumping, sneaking,
 * sprinting, attacking, interacting, opening their inventory, dropping items, swapping
 * offhand, picking blocks, or looking around - purely a client-side UX nicety layered on
 * top of [com.aaronmompie.game.freeze.FreezeManager]'s real server-side enforcement (position
 * pinning + block/entity/damage cancellation), which stays authoritative and unchanged; a
 * modified client could ignore this entirely. Chat/commands are deliberately left alone - a
 * frozen run still needs to be able to talk, and ops still need `/hardcore admin` to
 * unfreeze it.
 *
 * Movement/jump/sneak/sprint all route through [net.minecraft.client.player.LocalPlayer.input]
 * (a [ClientInput]), so swapping it for a blank one is enough to zero all of them at once -
 * confirmed via javap that the no-arg `ClientInput()` constructor sets
 * `keyPresses = Input.EMPTY` and `moveVector = Vec2.ZERO`, and that its `tick()` is a no-op, so
 * nothing recomputes it back to a non-zero value while swapped in.
 *
 * Everything else here (attack/use/inventory/drop/swap-offhand/pick-item/hotbar-number-keys) is
 * plain [KeyMapping]s instead, read directly off `Options` in vanilla's own client tick.
 * Blocking those needs two things, both confirmed via javap on [KeyMapping]'s bytecode:
 * `setDown(false)` for continuous polling (e.g. held-down mining), *and* draining `clickCount`
 * via a `consumeClick()` loop for one-shot actions (e.g. opening the inventory) - `setDown`
 * alone only touches the `isDown` flag, not the separate queued-click counter, so a real
 * keypress landing between our ticks could otherwise still slip one open-inventory click
 * through.
 *
 * Scroll-wheel hotbar switching doesn't go through a [KeyMapping] at all (the mouse handler
 * writes straight to [net.minecraft.world.entity.player.Inventory.setSelectedSlot]), so instead
 * of chasing that specific code path, the selected slot is pinned back every tick the same way
 * [com.aaronmompie.game.freeze.FreezeManager] already pins position server-side - self-correcting,
 * mechanism-agnostic, and consistent with that existing pattern.
 *
 * Mouse-look is pinned the same self-correcting way: yaw/pitch are captured at freeze start
 * and restored every END_CLIENT_TICK (which runs after vanilla applies that tick's mouse
 * deltas, but before the frame renders, so no movement is ever visible). The pin only applies
 * while the mouse is captured for looking ([net.minecraft.client.MouseHandler.isMouseGrabbed]
 * - present on every target) - any open screen (chat, admin panel, ...) releases the mouse,
 * so GUI interaction there is automatically exempt with no extra bookkeeping.
 */
object FreezeInputLock {
    private var savedInput: ClientInput? = null
    private var savedSlot: Int? = null
    private var savedYaw: Float? = null
    private var savedPitch: Float? = null

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val player = client.player
            if (player == null) {
                savedInput = null
                savedSlot = null
                return@register
            }
            if (FreezeClientState.frozen) {
                if (savedInput == null) {
                    savedInput = player.input
                    player.input = ClientInput()
                    savedYaw = player.getYRot()
                    savedPitch = player.getXRot()
                }
                if (savedSlot == null) {
                    savedSlot = player.inventory.selectedSlot
                }
                player.inventory.selectedSlot = savedSlot!!

                val options = client.options
                blockKey(options.keyAttack)
                blockKey(options.keyUse)
                blockKey(options.keyInventory)
                blockKey(options.keyDrop)
                blockKey(options.keySwapOffhand)
                blockKey(options.keyPickItem)
                for (hotbarKey in options.keyHotbarSlots) blockKey(hotbarKey)
            } else {
                savedInput?.let { player.input = it }
                savedInput = null
                savedSlot = null
                savedYaw = null
                savedPitch = null
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            val yaw = savedYaw
            val pitch = savedPitch
            if (FreezeClientState.frozen && yaw != null && pitch != null && client.mouseHandler.isMouseGrabbed()) {
                player.setYRot(yaw)
                player.setXRot(pitch)
            }
        }
    }

    private fun blockKey(mapping: KeyMapping) {
        mapping.setDown(false)
        while (mapping.consumeClick()) {
            // Drain any queued click so it can't fire once we stop blocking it this tick.
        }
    }
}
