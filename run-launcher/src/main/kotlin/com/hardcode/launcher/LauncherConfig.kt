package com.hardcode.launcher

import java.nio.file.Path
import java.nio.file.Paths

/**
 * All of this is env-var configured (matching the `HARDCODE_*` convention used elsewhere in
 * this project) rather than hardcoded, because how a game server is actually packaged and
 * launched is an ops decision this repo shouldn't bake in: a real deployment runs a Fabric
 * server launcher jar; the defaults here instead point at the Loom dev server so this class
 * is testable without a full production-style Fabric server install.
 */
data class LauncherConfig(
    /** Working directory the launch command itself is run from. */
    val launchWorkingDir: Path,
    /** Directory containing the server's world folder and server.properties. */
    val serverDir: Path,
    val worldDirName: String,
    val archiveWorlds: Boolean,
    val launchCommand: List<String>,
) {
    companion object {
        /** Launch-command entries are `||`-delimited since paths/args may contain spaces. */
        private const val COMMAND_DELIMITER = "||"

        fun fromEnv(): LauncherConfig {
            val launchWorkingDir = Paths.get(System.getenv("HARDCODE_LAUNCH_WORKING_DIR") ?: ".").toAbsolutePath()
            val serverDir = System.getenv("HARDCODE_SERVER_DIR")
                ?.let { Paths.get(it).toAbsolutePath() }
                ?: launchWorkingDir

            val defaultCommand = listOf(
                launchWorkingDir.resolve(if (isWindows()) "gradlew.bat" else "gradlew").toString(),
                ":game-server:runServer",
                "--no-daemon",
                "-q",
            ).joinToString(COMMAND_DELIMITER)

            return LauncherConfig(
                launchWorkingDir = launchWorkingDir,
                serverDir = serverDir,
                worldDirName = System.getenv("HARDCODE_WORLD_DIR_NAME") ?: "world",
                archiveWorlds = System.getenv("HARDCODE_ARCHIVE_WORLDS")?.toBooleanStrictOrNull() ?: true,
                launchCommand = (System.getenv("HARDCODE_LAUNCH_COMMAND") ?: defaultCommand).split(COMMAND_DELIMITER),
            )
        }

        private fun isWindows() = System.getProperty("os.name").contains("windows", ignoreCase = true)
    }
}
