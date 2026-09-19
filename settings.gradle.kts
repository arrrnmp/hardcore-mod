pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://papermc.io/repo/repository/maven-public/") { name = "PaperMC" }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

// No centralized dependencyResolutionManagement here (deliberately): fabric-loom injects
// its own project-level repositories (LoomLocalMinecraft, LoomGlobalMinecraft, ...) to serve
// synthetic per-project artifacts (the merged Minecraft jar, its library deps). Both
// FAIL_ON_PROJECT_REPOS and PREFER_SETTINGS suppress those injected repos, which breaks
// Loom entirely. Shared repos are declared the normal (default PREFER_PROJECT) way instead,
// via `allprojects { repositories { ... } }` in the root build.gradle.kts.

rootProject.name = "aaronmompie-mod"

include("common")
include("common-redis")
include("common-storage")
include("game-server")
include("limbo-server")
include("client")
include("proxy")
include("run-launcher")
