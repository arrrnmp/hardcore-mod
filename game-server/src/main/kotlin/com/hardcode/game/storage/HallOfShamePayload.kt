package com.hardcode.game.storage

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * S2C: JSON-encoded `List<`[com.hardcode.common.model.LeaderboardEntry]`>`, sent to *any*
 * player who asks (`/run halloffame`) - unlike the admin panel's snapshot, this one is
 * deliberately not operator-gated, per the project plan section 10 ("reachable by any
 * player"). Mirrored in client/src/main/kotlin/com/hardcode/client/storage/HallOfShamePayload.kt.
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
