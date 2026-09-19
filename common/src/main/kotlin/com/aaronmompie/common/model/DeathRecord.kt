package com.aaronmompie.common.model

import kotlinx.serialization.Serializable

/** One row of the Hall of Shame. Persisted to SQLite, never Redis (durable history). */
@Serializable
data class DeathRecord(
    val runId: String,
    val uuid: String,
    val playerName: String,
    val cause: String,
    val deathMessage: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String,
    val epochMillis: Long,
)

@Serializable
data class RunRecord(
    val runId: String,
    val seed: Long,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val outcome: RunOutcome? = null,
)

@Serializable
enum class RunOutcome { REROLLED, VOTE_FAILED, ADMIN_ENDED }
