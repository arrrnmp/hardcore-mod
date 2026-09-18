package com.hardcode.client.freeze

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Client-side mirror of the payload published from
 * game-server/src/main/kotlin/com/hardcode/game/freeze/FreezeStatePayload.kt - keep the
 * channel id and field layout in sync by hand (see that file's doc comment for why this
 * isn't pulled into a shared module).
 */
data class FreezeStatePayload(val frozen: Boolean, val waitingForPlayerName: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<FreezeStatePayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("hardcode", "freeze_state"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, FreezeStatePayload> = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FreezeStatePayload::frozen,
            ByteBufCodecs.STRING_UTF8,
            FreezeStatePayload::waitingForPlayerName,
            ::FreezeStatePayload,
        )
    }
}
