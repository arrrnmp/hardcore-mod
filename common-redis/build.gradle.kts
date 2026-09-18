plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    api(project(":common"))
    api(libs.jedis)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
}
