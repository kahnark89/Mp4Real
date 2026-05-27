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
        maven { url = uri("https://jitpack.io") }   // Polar BLE SDK
    }
}

rootProject.name = "arcshield-android"

// Only modules that currently have a build.gradle.kts are included.
// Stub modules still pending: source-emotibit, source-plc, llm-gemini
// (debrief-ui and source-openmeteo are now implemented above)
include(":app")
include(":core-capture")
include(":core-codec")
include(":core-llr")
include(":core-schema")
include(":shadow-mode-labeler")
include(":source-camerax")
include(":source-imu")
include(":source-polar")
include(":source-meta-raybans")
include(":source-vision-telemetry")
include(":llm-claude")
// Previously stubs, now implemented:
include(":source-openmeteo")
include(":debrief-ui")
