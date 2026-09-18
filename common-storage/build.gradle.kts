plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    api(project(":common"))
    api(libs.sqlite.jdbc)
    implementation(libs.slf4j.api)
}
