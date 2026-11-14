plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.xvpn"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.xvpn"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // One APK per CPU type instead of one 100MB+ APK holding every Xray build.
    // Most phones: install app-arm64-v8a-release.apk
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // Keep native libs compressed inside the APK (they are stored raw otherwise, which tripled the size)
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal-use build: sign with the debug key so the APK is installable.
            // Swap in your own signingConfig for anything you distribute.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Xray core (gomobile AAR from 2dust/AndroidLibXrayLite) -> put it in app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
