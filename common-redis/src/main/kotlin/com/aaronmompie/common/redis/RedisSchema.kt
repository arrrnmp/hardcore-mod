package com.aaronmompie.common.redis

/**
 * Central registry of every Redis key/channel the suite uses, so proxy, run-launcher,
 * game-server and limbo-server never hand-roll a string and drift apart.
 *
 * Keys are namespaced `hardcore:<area>:<...>`; pub/sub channels are `hardcore:events:<name>`.
 */
object RedisSchema {
    // --- Live state keys (ephemeral - source of truth is SQLite for anything durable) ---
    fun currentRunKey() = "hardcode:run:current"
    fun rosterKey(runId: String) = "hardcode:run:$runId:roster"
    fun freezeReasonKey(runId: String) = "hardcode:run:$runId:frozen-by"

    // --- Pub/sub channels for cross-process orchestration ---
    object Channels {
        /** Published by proxy when a roster member's connection drops. */
        const val ROSTER_MEMBER_DISCONNECTED = "hardcode:events:roster-member-disconnected"

        /** Published by proxy when a previously-disconnected roster member reconnects. */
        const val ROSTER_MEMBER_RECONNECTED = "hardcode:events:roster-member-reconnected"

        /** Published by game-server once a death vote passes. Payload: run id + new seed. */
        const val REROLL_REQUESTED = "hardcode:events:reroll-requested"

        /** Published by run-launcher once the new world process has finished archiving/
         *  regenerating and the game-server mod has confirmed spawn chunks are ready. */
        const val REROLL_READY = "hardcode:events:reroll-ready"

        /** Published by game-server/admin panel when an operator kicks a player from the run. */
        const val PLAYER_KICKED_FROM_RUN = "hardcode:events:player-kicked-from-run"
    }
}
