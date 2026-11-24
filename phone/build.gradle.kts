plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Gibber Phone: your own calls, texts and contacts in one app. It is its own app (not part of the
// launcher) so Android can make it the default for calls and texts.
android {
    namespace = "codes.gibber.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "codes.gibber.phone"
        minSdk = 29
        targetSdk = 35
        // Each GitHub build of the phone app gets the next number, which its Update button compares.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.$build"
    }

    // Same fixed key as the launcher, so every new version installs over the old one.
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    testImplementation("junit:junit:4.13.2")
}
