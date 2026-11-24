plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "codes.gibber.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "codes.gibber.launcher"
        minSdk = 26
        targetSdk = 35
        // Each GitHub build gets the next number, which the in-app Update button compares.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.$build"
    }

    // One fixed key, so every new version installs over the old one. GitHub's build passes the
    // key file and its password in; a build on your own PC falls back to the debug key.
    val keystore = System.getenv("LAUNCHER_KEYSTORE")
    signingConfigs {
        if (keystore != null) {
            create("fixed") {
                storeFile = file(keystore)
                storePassword = System.getenv("LAUNCHER_KEY_PASSWORD")
                keyAlias = "launcher"
                keyPassword = System.getenv("LAUNCHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystore != null) signingConfigs.getByName("fixed") else signingConfigs.getByName("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // Draws a game's SVG icon (Vite and many web games ship only an SVG favicon).
    implementation("com.caverock:androidsvg-aar:1.4")
}
