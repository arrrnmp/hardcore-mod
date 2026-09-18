package com.hardcode.common.storage

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Thin SQLite wrapper shared by game-server and run-launcher. Single file, WAL mode, so it
 * can be read concurrently while the game server writes deaths/run records.
 */
class Database(dbPath: Path) : AutoCloseable {
    val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")

    init {
        connection.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        connection.createStatement().use { it.execute(SCHEMA_RUNS) }
        connection.createStatement().use { it.execute(SCHEMA_DEATHS) }
        connection.createStatement().use { it.execute(SCHEMA_PLAYER_RUN_PROFILES) }
    }

    override fun close() = connection.close()

    companion object {
        private const val SCHEMA_RUNS = """
            CREATE TABLE IF NOT EXISTS runs (
                run_id TEXT PRIMARY KEY,
                seed INTEGER NOT NULL,
                started_at_epoch_millis INTEGER NOT NULL,
                ended_at_epoch_millis INTEGER,
                outcome TEXT
            )
        """

        private const val SCHEMA_DEATHS = """
            CREATE TABLE IF NOT EXISTS deaths (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL,
                uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                cause TEXT NOT NULL,
                death_message TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                dimension TEXT NOT NULL,
                epoch_millis INTEGER NOT NULL,
                FOREIGN KEY (run_id) REFERENCES runs(run_id)
            )
        """

        // Per-run, per-player data used by the admin "kick from run" action (wipe on kick).
        private const val SCHEMA_PLAYER_RUN_PROFILES = """
            CREATE TABLE IF NOT EXISTS player_run_profiles (
                run_id TEXT NOT NULL,
                uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                joined_at_epoch_millis INTEGER NOT NULL,
                kicked INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (run_id, uuid)
            )
        """
    }
}
