plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.blackhole.downloader"
    compileSdk = 35

    // Deterministic release signing. CI runners are ephemeral: each run used to
    // auto-generate a throwaway debug keystore, so every "latest" APK conflicted
    // with the previously installed one (signature mismatch). A repo-local
    // keystore makes all builds — local and CI — mutually upgradable.
    // One-time setup: bash make-keystore.sh  (then commit keystore/blackhole.jks)
    val keystoreFile = rootProject.file("keystore/blackhole.jks")
    val hasReleaseKey = keystoreFile.exists()

    defaultConfig {
        applicationId = "com.blackhole.downloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"

        // youtubedl-android ships native python/ffmpeg binaries for these ABIs only.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // Per-ABI APKs keep the install size down (~45 MB instead of ~150 MB).
    // The universal APK is also produced if you just want one file to sideload.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    packaging {
        // Required: the bundled python runtime must be extracted to disk to be executable.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCY")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("blackhole") {
                storeFile = keystoreFile
                // The key exists to keep updates installable over each other,
                // not as a secret; defaults match make-keystore.sh.
                storePassword = (findProperty("BLACKHOLE_STORE_PASSWORD") as String?) ?: "blackhole"
                keyAlias = (findProperty("BLACKHOLE_KEY_ALIAS") as String?) ?: "blackhole"
                keyPassword = (findProperty("BLACKHOLE_KEY_PASSWORD") as String?) ?: "blackhole"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Deterministic key when present; otherwise fall back so a missing
            // keystore never breaks the build (CI generates one via release.sh).
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("blackhole")
            } else {
                signingConfigs.getByName("debug")
            }
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
    }
}

dependencies {
    val ytdlp = "0.18.1"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // yt-dlp + python 3.8 bundled for Android. GPL-3.0.
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlp")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlp")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$ytdlp")
}
