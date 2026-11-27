// RBSB-Weather sync format v1: the shared module. Created By Shane BlackbuRn
//
// Plain Kotlin with NO Android dependencies, so RBSB-Weather for Android (0.8.0, if approved) can
// take an exact copy of src/main and run the same tests. It uses org.json because Android has it
// built in (the phone app already uses it): on Android it comes from the platform, so here it is
// compileOnly, and the JVM tests use the Maven copy.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly("org.json:json:20250517")
    testImplementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // The sample files are shared with the independent Python check (scripts/check-samples.py).
    systemProperty("rbsb.samples", file("samples").absolutePath)
    // The JVM's accepted time-zone names are written here; CI compares them with Python's (check-samples.py).
    systemProperty("rbsb.zoneList", layout.buildDirectory.file("java-timezones.txt").get().asFile.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
