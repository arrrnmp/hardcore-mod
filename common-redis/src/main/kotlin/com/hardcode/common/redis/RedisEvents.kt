package com.hardcode.common.redis

import kotlinx.serialization.Serializable

/** Payloads published on [RedisSchema.Channels]. Serialized as JSON via kotlinx.serialization. */
sealed interface RedisEvent {
    @Serializable
    data class RosterMemberDisconnected(val runId: String, val uuid: String) : RedisEvent

    @Serializable
    data class RosterMemberReconnected(val runId: String, val uuid: String) : RedisEvent

    @Serializable
    data class RerollRequested(val previousRunId: String, val newRunId: String, val newSeed: Long) : RedisEvent

    @Serializable
    data class RerollReady(val runId: String) : RedisEvent

    @Serializable
    data class PlayerKickedFromRun(val runId: String, val uuid: String) : RedisEvent
}
