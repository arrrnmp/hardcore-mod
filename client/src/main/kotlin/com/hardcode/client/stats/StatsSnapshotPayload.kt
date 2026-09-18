package com.hardcode.client.stats

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Client-side mirror of game-server/src/main/kotlin/com/hardcode/game/stats/StatsSnapshotPayload.kt
 * - keep the channel id and field layout in sync by hand.
 */
data class StatsSnapshotPayload(val snapshotJson: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<StatsSnapshotPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("hardcode", "stats_snapshot"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, StatsSnapshotPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            StatsSnapshotPayload::snapshotJson,
            ::StatsSnapshotPayload,
        )
    }
}
