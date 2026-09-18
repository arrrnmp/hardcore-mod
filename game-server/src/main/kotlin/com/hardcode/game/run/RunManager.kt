package com.hardcode.game.run

import com.hardcode.common.model.RunState
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

/**
 * Owns the current run's lifecycle state and roster.
 *
 * [runId] normally starts as a fresh random UUID, but when run-launcher relaunches the game
 * server after a reroll (Phase 2), it sets the `HARDCODE_RUN_ID` env var to the run id that
 * was already announced (Hall of Shame rows, chat messages, etc. before the restart) so this
 * process picks up the *same* id instead of minting a second one for what is really one run.
 *
 * [seed] starts as a random placeholder too, but is corrected to the real generated world
 * seed once the world exists (see [confirmSeed]) - run-launcher controls the actual seed via
 * `level-seed` in server.properties, not via anything this class generates.
 */
class RunManager(runIdOverride: String? = System.getenv("HARDCODE_RUN_ID")) {
    private val logger = LoggerFactory.getLogger("hardcore-game")

    val runId: String = runIdOverride ?: UUID.randomUUID().toString()

    var seed: Long = Random.nextLong()
        private set

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

    /** Called once the overworld exists, so logs/Hall of Shame reflect the real world seed. */
    fun confirmSeed(actualSeed: Long) {
        seed = actualSeed
    }
}
