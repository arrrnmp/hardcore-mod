# hardcore-mod

Hardcore Minecraft run system: one shared hardcore run across a Fabric game server, a Limbo waiting room, a Velocity proxy, and a required client mod — with death votes, freeze-on-disconnect, and automatic world re-rolls.

## How it works

1. Everyone plays survival hardcore together on the **game** server as one shared run.
2. A death ends the run. Players vote (`/run vote yes|no`) on whether to re-roll a fresh world.
3. On a passed vote, players are moved to the **limbo** server (a small void platform), the game server shuts down, and **run-launcher** archives the old world, writes a new seed, and relaunches the game server.
4. The **proxy** routes players back into the fresh world once it's ready.
5. If a roster member disconnects mid-run, the run **freezes** until they return, so nobody can progress while someone is gone.

## Modules

| Module | Type | What it does |
|---|---|---|
| `game-server` | Fabric mod | Run lifecycle, death handling, re-roll voting/coordination, freeze enforcement, admin service, stats broadcast, Hall of Shame, health tab list |
| `limbo-server` | Fabric mod | Waiting-room server: adventure mode + flight + invulnerability, void safety net, re-roll status broadcast |
| `client` | Fabric client mod | Freeze fog/banner + input lock, death flash, stats overlay, admin panel, Hall of Shame screen |
| `proxy` | Velocity plugin | Routes players between `game` and `limbo` based on run state; `/server <name>` switch command |
| `run-launcher` | Standalone JVM app | Supervises the game server process; owns world-folder archive/seed-rotation on re-roll |
| `common` | Library | Shared models (`RunState`, stats, death/admin snapshots) |
| `common-redis` | Library | Redis event bus + channel/key schema for cross-process orchestration |
| `common-storage` | Library | SQLite persistence (runs, players, Hall of Shame) |
| `local-test/` | Playtest env (gitignored) | Version-matched game+limbo pairs, proxy, Redis, launcher scripts — see `local-test/README.md` |

Redis (`hardcode:events:*`) links the pieces: roster disconnect/reconnect, `reroll-requested`, `reroll-ready`, player kicks. Everything degrades gracefully without Redis, but cross-server routing and re-rolls need it.

## Commands

- `/run vote yes|no` — vote during the post-death window
- `/run status` — run id / state / vote status
- `/run halloffame` — Hall of Shame leaderboard (any player)
- `/hardcore admin` — admin panel (operators only)
- `/server <name>` — proxy-only manual server switch (testing)

## Build

Requires Java 25 toolchain (Gradle handles it).

```powershell
./gradlew build
```

Targets Minecraft `26.3` by default. For older worldgen-mod compatibility:

```powershell
./gradlew build -PmcVersion="26.2"    # quotes required
./gradlew build -PmcVersion="26.1.2"
```

Per-module jars land in `<module>/build/libs/` (`run-launcher` ships the `-all.jar` shadow jar). Copy them into the matching server's `mods/` (or proxy's `plugins/`) — see `local-test/README.md` for the full manual-playtest setup.

## Tech

Kotlin · Fabric Loom (MC 26.1–26.3, Mojang-mapped, no remap step) · Fabric API · Velocity 4 · Jedis (Redis) · SQLite · kotlinx.serialization/coroutines

## License

MIT — see [LICENSE](LICENSE).
