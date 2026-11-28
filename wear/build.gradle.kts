// RBSB-Weather for Galaxy Watch: the Wear OS app. Created By Shane BlackbuRn
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Must stay identical to the phone app's package so the Wear OS Data Layer can link them.
    namespace = "io.github.shanebl030glitch.rbsbweather"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.shanebl030glitch.rbsbweather"
        minSdk = 33                     // Wear OS 4: what the Galaxy Watch6 shipped with
        targetSdk = 36                  // Wear OS 6 / Android 16 behaviour (see docs/SDK_LEVELS.md)
        // Watch version codes live in their own range so they can never be confused with the
        // phone app's (14 for 0.7.2): 1_000_000 + major*10_000 + minor*100 + patch.
        versionCode = 1_000_100
        versionName = "0.1.0-dev"
    }

    signingConfigs {
        // The shared, PUBLIC debug key (same file as RBSB-Weather for Android's test key).
        // Development builds only. Production signing is never configured in this file.
        create("sharedDebug") {
            storeFile = rootProject.file("keystore/rbsb-test.keystore")
            storePassword = "rbsbtest"
            keyAlias = "rbsbtest"
            keyPassword = "rbsbtest"
        }
    }

    buildTypes {
        getByName("debug") {
            // Development builds install beside the real app: ...rbsbweather.dev
            applicationIdSuffix = ".dev"
            versionNameSuffix = "+debug"
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
        getByName("release") {
            isMinifyEnabled = false
            // No signingConfig: release APKs come out unsigned and are signed only by the
            // protected release workflow (a later stage). The public debug key is never used.
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
        buildConfig = true
    }
    sourceSets {
        // Stage 2 sample data: the Stage 1 valid snapshots, copied in at build time (one source of truth).
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/sampleAssets"))
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true          // Robolectric render tests read the Moon textures
        unitTests.all { it.systemProperty("rbsb.samples", rootProject.file("sync/samples").absolutePath) }
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        xmlReport = true
        htmlReport = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")      // HorizontalPager (sky and About pages)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")   // the sky animates only while the app is in front
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")

    // Stage 1: the sync format the phone will send (not used by any screen yet; Stage 3 adds the receiver).
    implementation(project(":sync"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")          // the real org.json for JVM tests (Android's is a stub there)
    // Test-only (approved Stage 2 decision 6): render tests on Android 16 (SDK 36) without a device.
    testImplementation("org.robolectric:robolectric:4.18.1")
}

// Copies sync/samples/snapshot/valid/*.json into the app's assets as samples/*.json (Stage 2 sample data).
val copySampleAssets by tasks.registering(Sync::class) {
    from(rootProject.file("sync/samples/snapshot/valid")) { include("*.json") }
    into(layout.buildDirectory.dir("generated/sampleAssets/samples"))
}
tasks.named("preBuild") { dependsOn(copySampleAssets) }

// Robolectric needs Java 21 to run Android 16 (SDK 36) in the render tests (found in CI, Stage 2
// decision 6). Only the unit tests run on 21; the app is still compiled for Java 17. CI installs
// both (ci.yml); Gradle finds 21 through JAVA_HOME_21_X64 (gradle.properties) or the running JDK.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
