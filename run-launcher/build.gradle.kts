plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.shadow)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common-redis"))
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("com.aaronmompie.launcher.MainKt")
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.WARN
    // Keep the default "-all" classifier (not ""): the plain `application`-plugin jar
    // task also writes to the unclassified path, and colliding on it broke distZip/
    // distTar/startScripts' implicit-dependency validation. `shadowJar` output
    // (run-launcher-<version>-all.jar) is the one meant for standalone deployment.
    mergeServiceFiles()
}
