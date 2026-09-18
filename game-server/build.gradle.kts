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

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    implementation("net.fabricmc:fabric-loader:${libs.versions.fabricLoader.get()}")
    implementation(libs.fabric.api)
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
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
