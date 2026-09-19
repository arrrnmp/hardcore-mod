package com.aaronmompie.game.admin

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * S2C: the admin panel's data, JSON-encoded (see [com.aaronmompie.common.model.AdminSnapshot]).
 * Mirrored in client/src/main/kotlin/com/hardcode/client/admin/AdminPayloads.kt - keep the
 * channel ids and field layout in sync by hand (same reasoning as the freeze payload).
 */
data class AdminSnapshotPayload(val snapshotJson: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<AdminSnapshotPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("hardcode", "admin_snapshot"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AdminSnapshotPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            AdminSnapshotPayload::snapshotJson,
            ::AdminSnapshotPayload,
        )
    }
}

/** C2S: an admin action, JSON-encoded (see [com.aaronmompie.common.model.AdminAction]). */
data class AdminActionPayload(val actionJson: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<AdminActionPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath("hardcode", "admin_action"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            AdminActionPayload::actionJson,
            ::AdminActionPayload,
        )
    }
}
