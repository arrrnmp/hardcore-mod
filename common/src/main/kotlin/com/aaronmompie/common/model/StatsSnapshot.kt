package com.aaronmompie.common.model

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
    // Air supply for the drowning indicator - defaulted (rather than required) so a payload
    // from a build that doesn't send it yet still decodes instead of dropping the snapshot.
    val air: Int = 300,
    val maxAir: Int = 300,
    // Experience + absorption for the sprite overlay - defaulted like air so older
    // payloads still decode instead of dropping the snapshot.
    val expProgress: Float = 0f,
    val expLevel: Int = 0,
    val absorption: Float = 0f,
)

@Serializable
data class StatsSnapshot(val players: List<PlayerStat>)
