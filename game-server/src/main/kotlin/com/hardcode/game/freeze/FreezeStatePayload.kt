package com.hardcode.game.freeze

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * S2C payload telling the client whether the run is currently frozen and, if so, who it's
 * waiting for. [waitingForPlayerName] is "" when not frozen.
 *
 * Mirrored (not shared - see the client module's copy at
 * client/src/main/kotlin/com/hardcode/client/freeze/FreezeStatePayload.kt) since it's the
 * one small protocol record shared between two Fabric-aware modules; keep the channel id and
 * field layout in sync by hand rather than pulling in a new shared module for this alone.
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
