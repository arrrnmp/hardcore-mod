package com.hardcode.game.storage

import com.hardcode.common.model.DeathRecord
import com.hardcode.common.storage.Database

/** Hall of Shame / run-history queries on top of the shared SQLite [Database]. */
class HallOfShame(private val database: Database) {
    fun ensureRun(runId: String, seed: Long, startedAtEpochMillis: Long) {
        database.connection.prepareStatement(
            "INSERT OR IGNORE INTO runs (run_id, seed, started_at_epoch_millis) VALUES (?, ?, ?)",
        ).use { stmt ->
            stmt.setString(1, runId)
            stmt.setLong(2, seed)
            stmt.setLong(3, startedAtEpochMillis)
            stmt.executeUpdate()
        }
    }

    fun ensurePlayerProfile(runId: String, uuid: String, playerName: String, joinedAtEpochMillis: Long) {
        database.connection.prepareStatement(
            """
            INSERT OR IGNORE INTO player_run_profiles (run_id, uuid, player_name, joined_at_epoch_millis)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, runId)
            stmt.setString(2, uuid)
            stmt.setString(3, playerName)
            stmt.setLong(4, joinedAtEpochMillis)
            stmt.executeUpdate()
        }
    }

    fun recordDeath(record: DeathRecord) {
        database.connection.prepareStatement(
            """
            INSERT INTO deaths (run_id, uuid, player_name, cause, death_message, x, y, z, dimension, epoch_millis)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, record.runId)
            stmt.setString(2, record.uuid)
            stmt.setString(3, record.playerName)
            stmt.setString(4, record.cause)
            stmt.setString(5, record.deathMessage)
            stmt.setDouble(6, record.x)
            stmt.setDouble(7, record.y)
            stmt.setDouble(8, record.z)
            stmt.setString(9, record.dimension)
            stmt.setLong(10, record.epochMillis)
            stmt.executeUpdate()
        }
    }

    /** Total death count for [uuid] across every run - the headline Hall of Shame stat. */
    fun totalDeaths(uuid: String): Int {
        database.connection.prepareStatement("SELECT COUNT(*) FROM deaths WHERE uuid = ?").use { stmt ->
            stmt.setString(1, uuid)
            stmt.executeQuery().use { rs -> return if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    /** Leaderboard rows: player name, uuid, death count - highest deaths first. */
    fun leaderboard(limit: Int = 10): List<LeaderboardRow> {
        database.connection.prepareStatement(
            """
            SELECT uuid, MAX(player_name) AS player_name, COUNT(*) AS deaths
            FROM deaths
            GROUP BY uuid
            ORDER BY deaths DESC
            LIMIT ?
            """.trimIndent(),
        ).use { stmt ->
            stmt.setInt(1, limit)
            stmt.executeQuery().use { rs ->
                val rows = mutableListOf<LeaderboardRow>()
                while (rs.next()) {
                    rows += LeaderboardRow(rs.getString("uuid"), rs.getString("player_name"), rs.getInt("deaths"))
                }
                return rows
            }
        }
    }

    data class LeaderboardRow(val uuid: String, val playerName: String, val deaths: Int)
}
