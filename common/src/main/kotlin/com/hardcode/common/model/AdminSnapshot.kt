package com.hardcode.common.model

import kotlinx.serialization.Serializable

/**
 * Everything the admin panel needs to render itself, sent from game-server to client as a
 * single JSON-encoded field of a custom payload (see AdminSnapshotPayload in both the
 * game-server and client modules) - simpler and lower-risk than composing Minecraft's raw
 * StreamCodec for nested lists/records.
 */
@Serializable
data class AdminSnapshot(
    val runId: String,
    val state: String,
    val frozen: Boolean,
    val waitingForPlayerName: String,
    val roster: List<RosterEntry>,
    val leaderboard: List<LeaderboardEntry>,
    val voteDurationSeconds: Int,
    val maxFreezeMinutes: Int = 15,
)

@Serializable
data class RosterEntry(val uuid: String, val name: String, val online: Boolean, val dead: Boolean)

@Serializable
data class LeaderboardEntry(val name: String, val deaths: Int)

/**
 * A client-issued admin action, sent as a single JSON-encoded field of a custom payload (see
 * AdminActionPayload in both modules). [type] is one of [AdminActionType]'s names.
 */
@Serializable
data class AdminAction(
    val type: String,
    val targetUuid: String? = null,
    val voteDurationSeconds: Int? = null,
    val maxFreezeMinutes: Int? = null,
)

enum class AdminActionType {
    REQUEST_SNAPSHOT,
    FORCE_VOTE,
    FORCE_FREEZE,
    FORCE_UNFREEZE,
    FORCE_REROLL,
    KICK_PLAYER,
    UPDATE_CONFIG,
}
