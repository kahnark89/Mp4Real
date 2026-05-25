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

include(":app")
include(":core-capture")
include(":core-capture")
include(":core-codec")
include(":core-llr")
include(":core-schema")
include(":shadow-mode-labeler")
include(":source-camerax")
include(":source-polar")
include(":source-emotibit")
include(":source-meta-raybans")
include(":source-openmeteo")
include(":source-plc")
include(":source-vision-telemetry")
include(":llm-claude")
include(":llm-gemini")
include(":debrief-ui")
