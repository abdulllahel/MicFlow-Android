plugins {
    // The Android plugin compiles Kotlin itself (AGP 9+).
    id("com.android.application")
}

android {
    namespace = "com.micflow.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.micflow.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key for now, so it installs straight from this Mac.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
