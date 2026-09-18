package com.hardcode.launcher

import org.slf4j.LoggerFactory

/**
 * Standalone watchdog process that owns the game server's world-folder lifecycle and JVM
 * restarts for world re-rolls. Not a Minecraft mod - a plain supervisor process.
 *
 * Phase 0: scaffolding only. See the project plan, section 4, for the full design:
 * subscribe to `hardcode:events:reroll-requested`, archive/regenerate the run's world
 * folder under `runs/<run-id>/world/`, relaunch the game-server JVM, then publish
 * `hardcode:events:reroll-ready` once the new world's spawn chunks are confirmed loaded.
 */
fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("run-launcher")
    logger.info("Hardcore run-launcher starting (scaffolding phase)")
}
