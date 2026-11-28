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
        // On-device tests on the emulators (Moon parity and memory; Data Layer from Stage 3).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // Sample data: the Stage 1 valid snapshots, copied in at build time (one source of truth).
        // Debug builds only (Stage 3 decision D2): a release APK never contains or shows sample weather.
        getByName("debug").assets.srcDir(layout.buildDirectory.dir("generated/sampleAssets"))
        // Moon parity helpers shared by the Robolectric and on-device tests, and the Desktop
        // reference pictures (test resources) as on-device test assets (Moon decisions D7 to D10).
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
        getByName("androidTest").assets.srcDir("src/test/resources")
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

    // Stage 1: the sync format the phone sends; Stage 3 receives it.
    implementation(project(":sync"))

    // Stage 3 (decision D6, verified on claude/moon-verification: no permissions, no exported
    // components added to the release app; CI keeps checking that).
    implementation("com.google.android.gms:play-services-wearable:20.0.1")       // Data Layer
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")             // detail screens, swipe back
    testImplementation("androidx.compose.ui:ui-test-junit4")                     // screen tests (Robolectric)
    debugImplementation("androidx.compose.ui:ui-test-manifest")                  // debug only; its activity is kept unexported (src/debug)

    // On-device tests (emulators): real Android graphics, memory and the Data Layer.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("junit:junit:4.13.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")          // the real org.json for JVM tests (Android's is a stub there)
    // Test-only (approved Stage 2 decision 6): render tests on Android 16 (SDK 36) without a device.
    testImplementation("org.robolectric:robolectric:4.18.1")
}

// Copies sync/samples/snapshot/valid/*.json into the debug build's assets as samples/*.json (decision D2).
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
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
