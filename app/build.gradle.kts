plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.xvpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.outrun.vpn"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    splits {
        abi {
            isEnable = true
            reset()

            // -Pabis=arm64-v8a (comma separated) limits the build, e.g. for fast CI pull-request checks
            val all = (providers.gradleProperty("abis").orNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
            val have = all.filter { file("src/main/jniLibs/$it/libsingbox.so").exists() }
            include(*(have.ifEmpty { all }).toTypedArray())
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/DebugProbesKt.bin")
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libsingbox.so"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
