// Signing gate 1 probe (Stage 0 test tool, never released). Created By Shane BlackbuRn
//
// One small app, installed on BOTH a phone and a watch. CI signs it twice with throwaway keys:
//   phone copy: key A -> key B with a v3 rotation lineage (mirrors how Android 0.7.2 is signed)
//   watch copy: key B only (the approved plan for the RBSB watch app)
// If the two copies can exchange Data Layer messages, Google Play services treats them as
// "the same app", which is what signing gate 1 needs to prove.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Its own package: nothing to do with RBSB-Weather or Android 0.7.2.
    namespace = "io.github.shanebl030glitch.signingprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.shanebl030glitch.signingprobe"
        minSdk = 30                     // phone emulator (Android 11+) and watches (Wear OS 4+)
        targetSdk = 36
        versionCode = 1
        versionName = "stage0-probe"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Unsigned on purpose: the signing-probe workflow signs it with throwaway keys.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false            // a test tool: lint is reported for the app module only
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
