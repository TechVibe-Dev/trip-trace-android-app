import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Debug builds normally sign with an auto-generated, machine-specific
// keystore — fine for local development, but it means the SHA-1
// fingerprint (needed to restrict Google API keys to this app) is
// different on every CI runner, since GitHub Actions starts from a fresh
// machine each run. Decoding a fixed keystore committed to the repo here
// gives every build (CI or local) the same, stable SHA-1. Written under
// build/ (already gitignored) rather than the repo root, so the decoded
// binary can never end up committed by accident.
val debugKeystoreBase64 = rootProject.file("debug-keystore.base64")
val debugKeystoreFile = layout.buildDirectory.file("debug-keystore/debug.keystore").get().asFile
if (debugKeystoreBase64.exists()) {
    debugKeystoreFile.parentFile.mkdirs()
    debugKeystoreFile.writeBytes(Base64.getDecoder().decode(debugKeystoreBase64.readText().trim()))
}

android {
    namespace = "com.techvibedev.triptrace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techvibedev.triptrace"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
            if (debugKeystoreFile.exists()) {
                storeFile = debugKeystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.play.services.location)
}
