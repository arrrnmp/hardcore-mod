plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.fabric.loom) apply false
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "com.aaronmompie"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
    }
}
