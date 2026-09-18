package com.hardcode.launcher

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Owns a re-rolled game server's world folder and the `level-seed` line in its
 * server.properties - the two things that must change for a genuinely fresh world, per the
 * project plan's "full fresh world folder + process restart" strategy (worldgen mods like
 * Terralith explicitly warn against regenerating a world in place instead).
 */
class WorldFolderManager(
    private val serverDir: Path,
    private val worldDirName: String = "world",
    private val archiveInsteadOfDelete: Boolean = true,
) {
    private val logger = LoggerFactory.getLogger("run-launcher")

    /** Archives (or deletes) the previous run's world and writes the new seed for the next boot. */
    fun applyReroll(previousRunId: String, newSeed: Long) {
        val worldDir = serverDir.resolve(worldDirName)
        if (worldDir.exists()) {
            if (archiveInsteadOfDelete) {
                val archiveDir = serverDir.resolve("run-archives").resolve(previousRunId)
                Files.createDirectories(archiveDir.parent)
                Files.move(worldDir, archiveDir, StandardCopyOption.REPLACE_EXISTING)
                logger.info("Archived world for run {} to {}", previousRunId, archiveDir)
            } else {
                deleteRecursively(worldDir)
                logger.info("Deleted world for run {}", previousRunId)
            }
        }

        writeLevelSeed(newSeed)
    }

    private fun writeLevelSeed(newSeed: Long) {
        val propertiesFile = serverDir.resolve("server.properties")
        val existingLines = if (propertiesFile.exists()) Files.readAllLines(propertiesFile) else emptyList()

        var replaced = false
        val updatedLines = existingLines.map { line ->
            if (line.startsWith("level-seed=")) {
                replaced = true
                "level-seed=$newSeed"
            } else {
                line
            }
        }.toMutableList()

        if (!replaced) updatedLines += "level-seed=$newSeed"

        Files.createDirectories(serverDir)
        Files.write(propertiesFile, updatedLines)
        logger.info("Wrote level-seed={} to {}", newSeed, propertiesFile)
    }

    private fun deleteRecursively(path: Path) {
        if (!path.isDirectory()) {
            Files.deleteIfExists(path)
            return
        }
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { p ->
                try {
                    Files.delete(p)
                } catch (e: IOException) {
                    logger.warn("Failed to delete {}: {}", p, e.message)
                }
            }
        }
    }
}
