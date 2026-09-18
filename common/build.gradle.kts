plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    // Pure protocol/model module: DTOs + kotlinx.serialization only. This is the one
    // shared module the client mod also depends on, so it must stay free of
    // server-only dependencies (Redis, JDBC) - see common-redis / common-storage.
    api(libs.kotlinx.serialization.json)
}
