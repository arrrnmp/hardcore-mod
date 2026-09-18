plugins {
    alias(libs.plugins.fabric.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

val shade = configurations.create("shade")

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    implementation("net.fabricmc:fabric-loader:${libs.versions.fabricLoader.get()}")
    implementation(libs.fabric.api)
    implementation(libs.fabric.language.kotlin)

    // Client is intentionally the leanest jar in the suite - protocol DTOs only, no
    // Redis/JDBC. Keep it that way; it's the one artifact every player must install, and it
    // must stay predictable alongside Sodium/Iris.
    shade(project(":common"))
    implementation(project(":common"))
}

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
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
