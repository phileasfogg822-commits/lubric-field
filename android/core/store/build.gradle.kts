plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.lubrcapp.lubrcapp.core.store"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":domain:common"))
    implementation(project(":domain:business"))
    implementation(project(":domain:user"))
    implementation(project(":core:repository"))

    implementation("androidx.core:core-ktx")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx")
    implementation("androidx.datastore:datastore-preferences")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")

    implementation("androidx.room:room-runtime")
    implementation("androidx.room:room-ktx")
    ksp("androidx.room:room-compiler")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
}
