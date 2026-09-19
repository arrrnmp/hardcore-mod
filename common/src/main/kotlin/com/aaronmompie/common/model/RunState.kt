package com.aaronmompie.common.model

import kotlinx.serialization.Serializable

/** Lifecycle state of the current run, owned by the game server and mirrored to Redis. */
@Serializable
enum class RunState {
    /** A run is in progress; the world is live and ticking normally (unless [FROZEN]). */
    RUN_ACTIVE,

    /** A roster member disconnected; the world is tick-gated until they return or are kicked. */
    FROZEN,

    /** Someone died; everyone else is spectating in place while a continue/stop vote runs. */
    VOTE_PENDING,

    /**
     * The vote failed (or no vote has passed yet since the death): this run is over for
     * good. Everyone stays spectating this same world - the only ways out are a future
     * passed vote or an operator forcing a re-roll. There is no going back to survival on
     * this world once a death has happened; the vote only ever decides *when* to move on,
     * never whether to keep playing this run.
     */
    RUN_ENDED,

    /** The vote passed (or an operator forced it); the game server world is being torn
     *  down and rebuilt on a new seed. */
    REROLLING,
}

@Serializable
enum class VoteChoice { YES, NO }

@Serializable
data class RunSnapshot(
    val runId: String,
    val seed: Long,
    val state: RunState,
    /** Every UUID (as string) that has joined this run at least once. */
    val roster: Set<String>,
    val startedAtEpochMillis: Long,
    val frozenReasonUuid: String? = null,
)
