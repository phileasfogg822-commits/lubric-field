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
    api(project(":domain:accounting"))
    api(project(":domain:payment"))
    testImplementation("junit:junit:4.13.2")
}
