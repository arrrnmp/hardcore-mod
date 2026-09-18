package com.hardcode.game.death

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * S2C: a bare trigger telling the client to play the death red-flash effect. No real payload
 * data - the [marker] field only exists because it's simpler and safer to reuse the proven
 * single-field-codec pattern than to hand-roll a zero-byte `StreamCodec.unit` for a
 * self-referencing singleton payload.
 *
 * Mirrored in client/src/main/kotlin/com/hardcode/client/death/DeathFlashPayload.kt - keep
 * the channel id in sync by hand (same reasoning as the other mirrored payloads).
 */
data class DeathFlashPayload(val marker: Boolean = true) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<DeathFlashPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("hardcode", "death_flash"))

        val CODEC: StreamCodec<ByteBuf, DeathFlashPayload> =
            ByteBufCodecs.BOOL.map(::DeathFlashPayload, DeathFlashPayload::marker)
    }
}
