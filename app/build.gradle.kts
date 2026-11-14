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

    // One APK per CPU type, each holding only its own sing-box binary.
    // Most phones: install app-arm64-v8a-release.apk
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // useLegacyPackaging: libs are extracted to nativeLibraryDir on install, which is required to exec
    // libsingbox.so as a process (and keeps the APK small).
    // keepDebugSymbols: libsingbox.so is a Go executable, don't let AGP run strip on it.
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libsingbox.so"
        }
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
}
