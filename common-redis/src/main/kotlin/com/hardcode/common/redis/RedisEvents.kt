package com.hardcode.common.redis

import kotlinx.serialization.Serializable

/** Payloads published on [RedisSchema.Channels]. Serialized as JSON via kotlinx.serialization. */
sealed interface RedisEvent {
    @Serializable
    data class RosterMemberDisconnected(val runId: String, val uuid: String) : RedisEvent

    @Serializable
    data class RosterMemberReconnected(val runId: String, val uuid: String) : RedisEvent

    /**
     * [yesVoterUuids] is who the proxy should move to Limbo now, then into the new game
     * server once [RerollReady] arrives for [newRunId] - see the plan's Phase 2 note on
     * Limbo↔Game player migration, closed by the proxy's RerollRoutingListener.
     */
    @Serializable
    data class RerollRequested(
        val previousRunId: String,
        val newRunId: String,
        val newSeed: Long,
        val yesVoterUuids: List<String> = emptyList(),
    ) : RedisEvent

    @Serializable
    data class RerollReady(val runId: String) : RedisEvent

    @Serializable
    data class PlayerKickedFromRun(val runId: String, val uuid: String) : RedisEvent
}
