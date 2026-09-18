package com.hardcode.game.run

import com.hardcode.common.model.RunState
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

/**
 * Owns the current run's lifecycle state and roster. Phase 1: single world, no reroll -
 * [runId]/[seed] are generated once at server start and don't change yet (that's Phase 2,
 * which is also when this starts mirroring to Redis for the proxy/limbo/run-launcher to
 * read - no point wiring that up before anything consumes it).
 */
class RunManager {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    val runId: String = UUID.randomUUID().toString()
    val seed: Long = Random.nextLong()

    var state: RunState = RunState.RUN_ACTIVE
        private set

    /** Every UUID that has joined this run at least once - must stay connected (Phase 4). */
    private val roster: MutableSet<UUID> = linkedSetOf()

    /** UUIDs that have died this run and are permanently spectator until the next run. */
    private val dead: MutableSet<UUID> = linkedSetOf()

    fun isParticipant(uuid: UUID): Boolean = uuid in roster

    fun isDead(uuid: UUID): Boolean = uuid in dead

    fun rosterSnapshot(): Set<UUID> = roster.toSet()

    /** Registers [player] as a run participant the first time they join. Idempotent. */
    fun ensureJoined(player: ServerPlayer) {
        val uuid = player.getUUID()
        if (roster.add(uuid)) {
            logger.info("{} joined run {}", player.getGameProfile().name, runId)
        }
    }

    fun markDead(uuid: UUID) {
        dead.add(uuid)
    }

    fun setState(newState: RunState) {
        state = newState
    }
}
