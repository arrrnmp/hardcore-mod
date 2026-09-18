plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/") { name = "PaperMC" }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    // Provided by Velocity at runtime - never bundle the API itself.
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    // Velocity's own classpath doesn't include the Kotlin stdlib or our shared modules,
    // so unlike the Fabric mods (which use Loom's remapJar) this plugin ships as a
    // shadowJar fat jar. Velocity plugins are plain JVM classes with a @Plugin-annotated
    // main class - Kotlin's Java-annotation interop means no special "language adapter"
    // is needed here the way Fabric needs one (see fabric-language-kotlin note elsewhere).
    implementation(project(":common"))
    implementation(project(":common-redis"))
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.WARN
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
