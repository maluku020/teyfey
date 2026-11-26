plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maluku.teyfey"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maluku.teyfey"
        minSdk = 26
        targetSdk = 34
        // GitHub Actions sets GITHUB_RUN_NUMBER: every build gets a higher version
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = 100 + (run ?: 0)
        versionName = if (run != null) "1.${5 + run}" else "1.6-dev"
    }

    // Same key as all earlier Teyfey versions, so updates install over the old app
    signingConfigs {
        create("teyfey") {
            storeFile = rootProject.file("teyfey.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // The hammer button builds "debug": make that build fast too (not debuggable = full speed on Chromecast).
        debug {
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("teyfey")
        }
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK can be installed directly.
            signingConfig = signingConfigs.getByName("teyfey")
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
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-cast:$media3")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    // FFmpeg audio decoder (AC3, E-AC3, DTS, TrueHD…) so films have sound on Chromecast/TV boxes.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Installs Compose's speed profiles on the device (faster start + scrolling)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}
