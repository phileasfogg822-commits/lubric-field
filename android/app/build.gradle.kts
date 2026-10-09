import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val deploymentProperties = Properties().apply {
    val file = rootProject.file("white-label/deployment.properties")
    require(file.isFile) { "Missing white-label deployment config: " + file.path }
    file.inputStream().use { input -> load(input) }
}

fun deploymentValue(key: String): String {
    providers.gradleProperty("wl." + key).orNull?.let { return it }
    return deploymentProperties.getProperty(key)
        ?: error("Missing white-label deployment key: " + key)
}

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.lubrcapp.lubrcapp"
    compileSdk = 36

    defaultConfig {
        applicationId = deploymentValue("applicationId")
        minSdk = 26
        targetSdk = 36
        versionCode = deploymentValue("versionCode").toInt()
        versionName = deploymentValue("versionName")

        resValue("string", "app_name", deploymentValue("appName"))
        manifestPlaceholders["appIcon"] = deploymentValue("launcherIcon")
        manifestPlaceholders["roundAppIcon"] = deploymentValue("roundLauncherIcon")

        buildConfigField("String", "DEPLOYMENT_APP_NAME", quotedBuildConfig(deploymentValue("appName")))
        buildConfigField("String", "DEPLOYMENT_API_ENDPOINT", quotedBuildConfig(deploymentValue("apiEndpoint")))
        buildConfigField("String", "DEPLOYMENT_ENVIRONMENT", quotedBuildConfig(deploymentValue("environment")))
        buildConfigField("String", "DEPLOYMENT_FIREBASE_PROJECT_ID", quotedBuildConfig(deploymentValue("firebaseProjectId")))
        buildConfigField("String", "DEPLOYMENT_LICENSE_ID", quotedBuildConfig(deploymentValue("licenseId")))
        buildConfigField("String", "DEPLOYMENT_INCLUDED_MODULES", quotedBuildConfig(deploymentValue("includedModules")))
        buildConfigField("String", "DEPLOYMENT_SUPPORT_EMAIL", quotedBuildConfig(deploymentValue("supportEmail")))
        buildConfigField("String", "DEPLOYMENT_SUPPORT_PHONE", quotedBuildConfig(deploymentValue("supportPhone")))
        buildConfigField("String", "DEPLOYMENT_TERMS_URL", quotedBuildConfig(deploymentValue("termsUrl")))
        buildConfigField("String", "DEPLOYMENT_PRIVACY_URL", quotedBuildConfig(deploymentValue("privacyUrl")))
        buildConfigField("String", "DEPLOYMENT_WEBSITE", quotedBuildConfig(deploymentValue("website")))
        buildConfigField("String", "DEPLOYMENT_LOGO_URI", quotedBuildConfig(deploymentValue("brand.logoUri")))
        buildConfigField("String", "DEPLOYMENT_PRIMARY_COLOR", quotedBuildConfig(deploymentValue("brand.primaryColor")))
        buildConfigField("String", "DEPLOYMENT_SECONDARY_COLOR", quotedBuildConfig(deploymentValue("brand.secondaryColor")))
        buildConfigField("String", "DEPLOYMENT_ACCENT_COLOR", quotedBuildConfig(deploymentValue("brand.accentColor")))
        buildConfigField("String", "DEPLOYMENT_SUCCESS_COLOR", quotedBuildConfig(deploymentValue("brand.successColor")))
        buildConfigField("String", "DEPLOYMENT_WARNING_COLOR", quotedBuildConfig(deploymentValue("brand.warningColor")))
        buildConfigField("String", "DEPLOYMENT_ERROR_COLOR", quotedBuildConfig(deploymentValue("brand.errorColor")))
        buildConfigField("String", "DEPLOYMENT_FONT_FAMILY", quotedBuildConfig(deploymentValue("brand.fontFamily")))
        buildConfigField("String", "DEPLOYMENT_CARD_STYLE", quotedBuildConfig(deploymentValue("brand.cardStyle")))
        buildConfigField("String", "DEPLOYMENT_BUTTON_STYLE", quotedBuildConfig(deploymentValue("brand.buttonStyle")))
        buildConfigField("int", "DEPLOYMENT_BORDER_RADIUS", deploymentValue("brand.borderRadius"))
        buildConfigField("String", "DEFAULT_TEXT_SCALE", quotedBuildConfig(deploymentValue("design.defaultTextScale")))
        buildConfigField("boolean", "DEFAULT_LARGE_TOUCH_TARGETS", deploymentValue("design.defaultLargeTouchTargets"))
        buildConfigField("String", "DEFAULT_LANGUAGE", quotedBuildConfig(deploymentValue("defaultLanguage")))
        buildConfigField("String", "DEFAULT_THEME_MODE", quotedBuildConfig(deploymentValue("defaultThemeMode")))
        buildConfigField("String", "TERM_SALE", quotedBuildConfig(deploymentValue("term.sale")))
        buildConfigField("String", "TERM_INVOICE", quotedBuildConfig(deploymentValue("term.invoice")))
        buildConfigField("String", "TERM_TAX_INVOICE", quotedBuildConfig(deploymentValue("term.taxInvoice")))
        buildConfigField("String", "TERM_CASH_MEMO", quotedBuildConfig(deploymentValue("term.cashMemo")))
        buildConfigField("String", "TERM_QUOTATION", quotedBuildConfig(deploymentValue("term.quotation")))
        buildConfigField("String", "TERM_ESTIMATE", quotedBuildConfig(deploymentValue("term.estimate")))
        buildConfigField("String", "TERM_CUSTOMER", quotedBuildConfig(deploymentValue("term.customer")))
        buildConfigField("String", "TERM_SUPPLIER", quotedBuildConfig(deploymentValue("term.supplier")))
        buildConfigField("String", "TERM_PARTY", quotedBuildConfig(deploymentValue("term.party")))
        buildConfigField("String", "TERM_ITEM", quotedBuildConfig(deploymentValue("term.item")))
        buildConfigField("String", "TERM_CATEGORY", quotedBuildConfig(deploymentValue("term.category")))
        buildConfigField("String", "TERM_BRAND", quotedBuildConfig(deploymentValue("term.brand")))
        buildConfigField("String", "TERM_UNIT", quotedBuildConfig(deploymentValue("term.unit")))
        buildConfigField("String", "TERM_WAREHOUSE", quotedBuildConfig(deploymentValue("term.warehouse")))
        buildConfigField("String", "TERM_PAYMENT_MODE", quotedBuildConfig(deploymentValue("term.paymentMode")))
        buildConfigField("String", "TERM_EXPENSE_CATEGORY", quotedBuildConfig(deploymentValue("term.expenseCategory")))
        buildConfigField("String", "TERM_OTHER_INCOME", quotedBuildConfig(deploymentValue("term.otherIncome")))
        buildConfigField("boolean", "DEFAULT_TAX_ENABLED", deploymentValue("tax.enabled"))
        buildConfigField("String", "DEFAULT_TAX_LABEL", quotedBuildConfig(deploymentValue("tax.label")))
        buildConfigField("String", "DEFAULT_TAX_NUMBER_LABEL", quotedBuildConfig(deploymentValue("tax.numberLabel")))
        buildConfigField("String", "DEFAULT_TAX_RATE", quotedBuildConfig(deploymentValue("tax.defaultRate")))
        buildConfigField("String", "DEFAULT_PAYMENT_METHODS", quotedBuildConfig(deploymentValue("paymentMethods")))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.navigation:navigation-compose")
    implementation("androidx.hilt:hilt-navigation-compose")

    // Core
    implementation("androidx.core:core-ktx")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.appcompat:appcompat")
    implementation("com.google.android.material:material")
    implementation("androidx.constraintlayout:constraintlayout")

    // DI
    implementation("com.google.dagger:hilt-android")
    ksp("com.google.dagger:hilt-compiler")

    // Data
    implementation("androidx.room:room-runtime")
    ksp("androidx.room:room-compiler")
    implementation("androidx.room:room-ktx")
    implementation("androidx.datastore:datastore-preferences")
    implementation("androidx.datastore:datastore-preferences-core")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Sync
    implementation("androidx.work:work-runtime-ktx")

    // Camera / Barcode
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Utils
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.compose.foundation:foundation")

    // Project modules
    implementation(project(":core:designsystem"))
    implementation(project(":core:auth"))
    implementation(project(":core:cache"))
    implementation(project(":core:network"))
    implementation(project(":core:permissions"))
    implementation(project(":core:store"))
    implementation(project(":core:repository"))
    implementation(project(":core:sync"))
    implementation(project(":core:ui"))

    implementation(project(":domain:business"))
    implementation(project(":domain:party"))
    implementation(project(":domain:item"))
    implementation(project(":domain:inventory"))
    implementation(project(":domain:sales"))
    implementation(project(":domain:purchase"))
    implementation(project(":domain:payment"))
    implementation(project(":domain:accounting"))
    implementation(project(":domain:reporting"))
    implementation(project(":domain:user"))

    implementation(project(":feature:dashboard"))
    implementation(project(":feature:accounts"))
    implementation(project(":feature:inventory"))
    implementation(project(":feature:sales"))
    implementation(project(":feature:purchases"))
    implementation(project(":feature:parties"))
    implementation(project(":feature:items"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:syncstatus"))

    implementation(project(":whitelist:config"))
    implementation(project(":whitelist:branding"))
    implementation(project(":whitelist:featureflags"))

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

hilt {
    ksp {
        arg("dagger.compiler.arg", "stripArgs")
    }
}

