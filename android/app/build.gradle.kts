import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)
}

// Read local.properties for secrets that must never be committed.
// Developers add CLAUDE_API_KEY=sk-ant-... and POLAR_DEVICE_ID=XXXXXXXX
// to android/local.properties (already in .gitignore).
val localProperties = Properties().also { props ->
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { props.load(it) }
}

android {
    namespace  = "com.capsconc.arcshield.app"
    compileSdk = 34

    defaultConfig {
        applicationId   = "com.capsconc.arcshield"
        minSdk          = 26
        targetSdk       = 34
        versionCode     = 1
        versionName     = "0.1.0-phase1"

        // Secrets injected at build time — never committed to source control.
        buildConfigField(
            "String", "CLAUDE_API_KEY",
            "\"${localProperties.getProperty("CLAUDE_API_KEY", "")}\""
        )
        buildConfigField(
            "String", "POLAR_DEVICE_ID",
            "\"${localProperties.getProperty("POLAR_DEVICE_ID", "")}\""
        )
        // Phase 1 facility constants — not secrets, but kept configurable.
        buildConfigField("String", "FACILITY_ID", "\"hollowell_industries\"")
        buildConfigField("String", "LINE_ID",     "\"ppvc_line_1\"")
    }

    buildFeatures {
        compose      = true
        buildConfig  = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ---- ArcShield modules ------------------------------------------------
    implementation(project(":core-capture"))
    implementation(project(":core-codec"))
    implementation(project(":core-llr"))
    implementation(project(":core-schema"))
    implementation(project(":shadow-mode-labeler"))
    implementation(project(":source-camerax"))
    implementation(project(":source-polar"))
    implementation(project(":source-vision-telemetry"))
    implementation(project(":llm-claude"))

    // ---- Hilt DI ----------------------------------------------------------
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ---- Compose ----------------------------------------------------------
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // ---- Android / Coroutines --------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
