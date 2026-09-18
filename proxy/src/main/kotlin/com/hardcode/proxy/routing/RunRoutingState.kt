package com.hardcode.proxy.routing

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks what the proxy needs to know to migrate players between Limbo and Game across a
 * re-roll, without the proxy having to ask game-server anything - see the plan's Phase 2
 * note on Limbo↔Game player migration.
 */
class RunRoutingState(private val pendingTimeoutMillis: Long = DEFAULT_PENDING_TIMEOUT_MILLIS) {
    companion object {
        /**
         * A real re-roll (world regen + JVM restart) can genuinely take well over a minute
         * for spawn chunk generation, so this needs to be generous - but it also has to
         * self-heal if `reroll-ready` never arrives at all (e.g. run-launcher isn't running,
         * as in the manual local-test setup), which was confirmed to get the proxy stuck
         * routing everyone to Limbo forever otherwise.
         */
        const val DEFAULT_PENDING_TIMEOUT_MILLIS = 90_000L
    }

    @Volatile
    private var pendingRerollRunId: String? = null

    @Volatile
    private var pendingSinceMillis: Long = 0

    private val yesVotersByRunId = ConcurrentHashMap<String, Set<UUID>>()

    /** True only while a reroll is both pending and recent - see [DEFAULT_PENDING_TIMEOUT_MILLIS]. */
    val isRerollPending: Boolean
        get() = pendingRerollRunId != null && (System.currentTimeMillis() - pendingSinceMillis) < pendingTimeoutMillis

    fun onRerollRequested(newRunId: String, yesVoters: Set<UUID>) {
        pendingRerollRunId = newRunId
        pendingSinceMillis = System.currentTimeMillis()
        yesVotersByRunId[newRunId] = yesVoters
    }

    /** Returns the yes-voters for [runId] and forgets them - each reroll's list is consumed once. */
    fun onRerollReady(runId: String): Set<UUID> {
        if (pendingRerollRunId == runId) pendingRerollRunId = null
        return yesVotersByRunId.remove(runId) ?: emptySet()
    }
}
