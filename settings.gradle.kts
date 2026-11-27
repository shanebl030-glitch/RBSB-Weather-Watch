// RBSB-Weather for Galaxy Watch. Created By Shane BlackbuRn
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RBSB-Weather-Watch"

// The Wear OS app (Stage 0: a minimal shell with an About screen only).
include(":wear")

// Stage 0 signing gate 1: a throwaway probe app used only to test that a key-rotated
// phone app and a new-key-only watch app can talk over the Wear OS Data Layer.
// It is never released and has its own package name (not RBSB's).
include(":signingprobe")
project(":signingprobe").projectDir = file("tools/signing-probe")
