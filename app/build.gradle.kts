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
            // only ABIs that have a sing-box binary (scripts/fetch-singbox.sh skips ones upstream doesn't publish)
            val all = listOf("arm64-v8a", "armeabi-v7a", "x86_64")
            val have = all.filter { file("src/main/jniLibs/$it/libsingbox.so").exists() }
            include(*(have.ifEmpty { all }).toTypedArray())
            isUniversalApk = false
        }
    }

    // useLegacyPackaging: libs are extracted to nativeLibraryDir on install, which is required to exec
    // libsingbox.so as a process (and keeps the APK small).
    // keepDebugSymbols: libsingbox.so is a Go executable, don't let AGP run strip on it.
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/DebugProbesKt.bin")
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libsingbox.so"
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking: drops unused Compose / material-icons-extended code (several MB)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    dependenciesInfo { includeInApk = false; includeInBundle = false }
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
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")   // QR scanner
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
