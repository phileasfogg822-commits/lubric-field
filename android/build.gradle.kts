plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("androidx.room") version "2.8.5" apply false
    id("com.android.library") version "8.13.2" apply false
}

val moduleVersions: Map<String, String> = mapOf(
    "androidx.core:core-ktx" to "1.19.0",
    "androidx.appcompat:appcompat" to "1.8.0",
    "com.google.android.material:material" to "1.13.0",
    "androidx.constraintlayout:constraintlayout" to "2.2.2",
    "androidx.lifecycle:lifecycle-runtime-ktx" to "2.11.0",
    "androidx.lifecycle:lifecycle-viewmodel-ktx" to "2.11.0",
    "androidx.lifecycle:lifecycle-viewmodel-compose" to "2.11.0",
    "androidx.lifecycle:lifecycle-runtime-compose" to "2.11.0",
    "androidx.lifecycle:lifecycle-livedata-ktx" to "2.11.0",
    "androidx.navigation:navigation-compose" to "2.10.2",
    "androidx.hilt:hilt-navigation-compose" to "1.4.0",
    "androidx.hilt:hilt-navigation" to "1.4.0",
    "com.google.dagger:hilt-android" to "2.57.2",
    "com.google.dagger:hilt-compiler" to "2.57.2",
    "androidx.room:room-runtime" to "2.8.5",
    "androidx.room:room-ktx" to "2.8.5",
    "androidx.room:room-compiler" to "2.8.5",
    "androidx.datastore:datastore" to "1.2.1",
    "androidx.datastore:datastore-core" to "1.2.1",
    "androidx.datastore:datastore-preferences" to "1.2.1",
    "androidx.datastore:datastore-preferences-core" to "1.2.1",
    "androidx.work:work-runtime-ktx" to "2.12.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core" to "1.11.0",
    "org.jetbrains.kotlinx:kotlinx-coroutines-android" to "1.11.0",
)

subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("com.google.dagger.hilt.android")) {
            dependencies {
                add("implementation", "com.google.dagger:hilt-android")
                add("ksp", "com.google.dagger:hilt-compiler")
            }
        }

        configurations.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.version.isNullOrEmpty()) {
                    val version = moduleVersions["${requested.group}:${requested.name}"]
                    if (version != null) {
                        useVersion(version)
                    }
                }
            }
        }
    }
}

task("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
