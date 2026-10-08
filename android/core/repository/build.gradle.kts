plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":domain:common"))
    api(project(":domain:business"))
    api(project(":domain:user"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
