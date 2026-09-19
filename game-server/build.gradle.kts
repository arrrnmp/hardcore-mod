plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Plain (non-mod) JVM dependencies that must be bundled INTO this mod's jar, since Fabric
// Loader only puts declared mods on the classpath, not arbitrary Gradle dependencies.
val shade = configurations.create("shade")

// Switchable Minecraft target: default 26.3 (gradle.properties `mcVersion`);
// `-PmcVersion="26.2"` or `-PmcVersion="26.1.2"` (quotes required) builds against an
// older MC for third-party mods not yet updated. fabricApiVersion follows automatically
// unless overridden with -PfabricApiVersion.
val mcVersion: String = providers.gradleProperty("mcVersion").getOrElse(libs.versions.minecraft.get())
val fabricApiVersion: String = providers.gradleProperty("fabricApiVersion").getOrElse(
    when (mcVersion) {
        "26.2" -> libs.versions.fabricApi262.get()
        "26.1.2" -> libs.versions.fabricApi2612.get()
        else -> libs.versions.fabricApi.get()
    }
)

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    implementation("net.fabricmc:fabric-loader:${libs.versions.fabricLoader.get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation(libs.fabric.language.kotlin)

    shade(project(":common"))
    shade(project(":common-redis"))
    shade(project(":common-storage"))
    implementation(project(":common"))
    implementation(project(":common-redis"))
    implementation(project(":common-storage"))
}

// No remap step on 26.1+ (nothing to remap) - shadowJar's output is the final jar.
tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.WARN
    configurations = listOf(shade)
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", mcVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft_version" to mcVersion)
    }
}
