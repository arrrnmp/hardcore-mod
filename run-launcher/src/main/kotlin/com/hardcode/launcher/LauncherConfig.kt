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

        const val MC_VERSION_ENV = "HARDCODE_MC_VERSION"

        /** MC targets this repo can build (see each Fabric module's `-PmcVersion`). */
        val SUPPORTED_MC_VERSIONS = setOf("26.3", "26.2", "26.1.2")

        const val DEFAULT_MC_VERSION = "26.3"

        /** Versioned game-server profile dir()['name'] under the local-test tree. */
        fun profileDirName(mcVersion: String) = "game-$mcVersion"

        fun fromEnv(mcVersion: String = DEFAULT_MC_VERSION): LauncherConfig {
            require(mcVersion in SUPPORTED_MC_VERSIONS) {
                "Unsupported MC version '$mcVersion' (supported: ${SUPPORTED_MC_VERSIONS.sorted()})"
            }
            // Pinned single-version deployments set HARDCODE_SERVER_DIR outright; otherwise the
            // server dir is the version profile (game-<mcVersion>) resolved against the working dir,
            // so one launcher checkout can supervise any locally installed version. The supervised
            // process always runs inside the server dir unless HARDCODE_LAUNCH_WORKING_DIR says
            // otherwise - server files (server.properties, world) resolve relative to it.
            val explicitServerDir = System.getenv("HARDCODE_SERVER_DIR")
            val serverDir = explicitServerDir
                ?.let { Paths.get(it).toAbsolutePath().normalize() }
                ?: Paths.get(System.getenv("HARDCODE_LAUNCH_WORKING_DIR") ?: ".").toAbsolutePath().normalize()
                    .resolve(profileDirName(mcVersion))
            val launchWorkingDir = System.getenv("HARDCODE_LAUNCH_WORKING_DIR")
                ?.let { Paths.get(it).toAbsolutePath().normalize() }
                ?: serverDir

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
