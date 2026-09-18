package com.hardcode.launcher

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Launches and supervises the actual game-server process. Deliberately agnostic to how that
 * server is packaged (a real deployment runs a Fabric server launcher jar; local testing
 * here runs the Loom dev server via Gradle) - [launchCommand] is fully configurable so this
 * class only knows "run this command in this directory with this run id".
 */
class GameServerProcess(
    private val workingDir: Path,
    private val launchCommand: List<String>,
) {
    private val logger = LoggerFactory.getLogger("run-launcher")
    private var process: Process? = null

    /** Starts the server with [runId] visible to it via the HARDCODE_RUN_ID env var. */
    fun start(runId: String) {
        logger.info("Launching game server for run {} in {}", runId, workingDir)
        val builder = ProcessBuilder(launchCommand)
            .directory(workingDir.toFile())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.environment()["HARDCODE_RUN_ID"] = runId
        process = builder.start()
    }

    /** Blocks until the process exits on its own (e.g. RerollCoordinator called server.halt). */
    fun awaitExit() {
        process?.waitFor()
    }

    /** Force-kills the process if it hasn't exited within [timeoutSeconds] of being asked to stop. */
    fun stopAndAwait(timeoutSeconds: Long = 30) {
        val proc = process ?: return
        if (!proc.isAlive) return
        if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            logger.warn("Game server didn't exit within {}s, forcing it down", timeoutSeconds)
            proc.destroyForcibly()
        }
    }

    val isAlive: Boolean
        get() = process?.isAlive == true
}
