package com.hardcode.client.death

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Client-side mirror of game-server/src/main/kotlin/com/hardcode/game/death/DeathFlashPayload.kt
 * - keep the channel id in sync by hand (same reasoning as the other mirrored payloads).
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
