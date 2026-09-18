package com.hardcode.common.model

import kotlinx.serialization.Serializable

/**
 * One participant's live stats, broadcast periodically (not per-tick) for the client's
 * statistics overlay - see the project plan, section 9.
 */
@Serializable
data class PlayerStat(
    val uuid: String,
    val name: String,
    val health: Float,
    val maxHealth: Float,
    val food: Int,
    val armor: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String,
)

@Serializable
data class StatsSnapshot(val players: List<PlayerStat>)
