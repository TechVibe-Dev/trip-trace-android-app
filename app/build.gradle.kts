import java.util.Base64
import java.util.Properties

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

// Maps API key — never hardcoded here. In CI it comes in as a Gradle
// project property (-PMAPS_API_KEY=..., sourced from a GitHub Actions
// secret); for local builds, from a MAPS_API_KEY line in local.properties
// (already gitignored, standard Android practice). Falls back to an empty
// string so a build without either still compiles — the map just won't
// load without a real key.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val mapsApiKey: String = (project.findProperty("MAPS_API_KEY") as String?)
    ?: localProperties.getProperty("MAPS_API_KEY")
    ?: ""

android {
    namespace = "com.techvibedev.triptrace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.techvibedev.triptrace"
        minSdk = 26
        targetSdk = 35
        // Kept in sync with the CHANGELOG version on every release from
        // here on (previously both were left at their initial placeholder
        // value since the project started). versionCode is the plain
        // internal counter Android itself uses to tell builds apart (never
        // shown to the user) — it only needs to strictly increase, so it
        // just goes up by 1 each release rather than encoding the version
        // number itself. versionName is the human-readable one shown in
        // Settings > Apps.
        versionCode = 2
        versionName = "0.4.0"

        manifestPlaceholders["mapsApiKey"] = mapsApiKey
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
    implementation(libs.maps.compose)
    // maps-compose 3.0.0 predates the version of the library that pulls
    // this in transitively (a later release consolidated it) — needs to be
    // declared explicitly here, matching what 3.0.0 itself was built/tested
    // against.
    implementation(libs.play.services.maps)
}

// maps-compose pulls in androidx.core:core-ktx/core transitively at a
// version newer than ours (1.15.0) — that newer version requires a
// compileSdk we're not on yet (android#21, deferred on purpose). We already
// declare core-ktx 1.15.0 ourselves and it's confirmed compileSdk-35-safe
// (per AndroidX's own release notes), so force every configuration to that
// version rather than whatever maps-compose's own dependency graph asks
// for. Narrower and more direct than hunting for an older maps-compose
// release that happens to predate the bump — and keeps working regardless
// of which maps-compose version we're on.
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core-ktx:${libs.versions.coreKtx.get()}",
            "androidx.core:core:${libs.versions.coreKtx.get()}",
        )
    }
}
