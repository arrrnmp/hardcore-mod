package com.hardcode.proxy.routing

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks what the proxy needs to know to migrate players between Limbo and Game across a
 * re-roll, without the proxy having to ask game-server anything - see the plan's Phase 2
 * note on Limbo↔Game player migration.
 */
class RunRoutingState {
    /** Set while a reroll has been requested but the new run isn't ready yet - the game
     *  server is mid-restart during this window, so new/reconnecting players should go to
     *  Limbo instead (Game would just refuse the connection). */
    @Volatile
    var pendingRerollRunId: String? = null
        private set

    private val yesVotersByRunId = ConcurrentHashMap<String, Set<UUID>>()

    fun onRerollRequested(newRunId: String, yesVoters: Set<UUID>) {
        pendingRerollRunId = newRunId
        yesVotersByRunId[newRunId] = yesVoters
    }

    /** Returns the yes-voters for [runId] and forgets them - each reroll's list is consumed once. */
    fun onRerollReady(runId: String): Set<UUID> {
        if (pendingRerollRunId == runId) pendingRerollRunId = null
        return yesVotersByRunId.remove(runId) ?: emptySet()
    }
}
