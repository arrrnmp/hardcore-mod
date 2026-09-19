package com.aaronmompie.game.stats

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * S2C: JSON-encoded [com.aaronmompie.common.model.StatsSnapshot] (same one-string-field pattern
 * as the admin panel's payloads - see game-server/src/main/kotlin/com/hardcode/game/admin/AdminPayloads.kt
 * for why). Mirrored in client/src/main/kotlin/com/hardcode/client/stats/StatsSnapshotPayload.kt.
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
