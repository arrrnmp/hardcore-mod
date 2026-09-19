package com.aaronmompie.client.storage

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Client-side mirror of game-server/src/main/kotlin/com/hardcode/game/storage/HallOfShamePayload.kt
 * - keep the channel id in sync by hand.
 */
data class HallOfShamePayload(val leaderboardJson: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<HallOfShamePayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("hardcode", "hall_of_shame"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HallOfShamePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            HallOfShamePayload::leaderboardJson,
            ::HallOfShamePayload,
        )
    }
}
