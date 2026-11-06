import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Current git branch at build time; "unknown" when git is unavailable or HEAD is detached.
// (Project.exec was removed in Gradle 9; providers.exec is the configuration-time replacement.)
val gitBranch: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
    }.standardOutput.asText.get().trim()
        .takeIf { it.isNotEmpty() && it != "HEAD" } ?: "unknown"
} catch (_: Exception) {
    "unknown"
}

android {
    namespace = "com.mpx.player"
    compileSdk = 36
    // Installed platform package is android-36-ext19 (API 36 + extension level 19).
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.mpx.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.0-beta"

        buildConfigField("String", "GIT_BRANCH", "\"$gitBranch\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // NOTE: no ndk abiFilters here — ABI splits below govern which ABIs
        // are packaged, and AGP rejects abiFilters when splits are set.
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // No universal (fat) APK: each split carries only its own .so files.
            // armeabi-v7a is NOT dropped — it ships as its own split APK.
            isUniversalApk = false
        }
    }

    buildTypes {
        // Minified test build: R8 + resource shrinking, still signed with the
        // debug key, so no release key is needed. Produces per-ABI splits
        // (app-arm64-v8a-minified.apk, app-armeabi-v7a-minified.apk).
        create("minified") {
            initWith(getByName("debug"))
            isDebuggable = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        // Issue #324: youtubedl-android ships native libs that must be
        // extracted at install time (useLegacyPackaging). AGP 9 refuses
        // android:extractNativeLibs="true" in the manifest — this is the
        // build-script equivalent.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.media:media:1.7.0")
    // MPV engine dependency (Issue #52)
    implementation("io.github.abdallahmehiz:mpv-android-lib:0.1.12") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "androidx.appcompat")
    }
    // Haze 2.0 visual effects (M2.5 UI revolution)
    implementation("dev.chrisbanes.haze:haze:1.6.0")
    // SMB client for Local Network browsing (issue #78): pure-Java SMB2/3,
    // Apache 2.0, no native code, no MX code.
    implementation("com.hierynomus:smbj:0.12.0")
    // Downloader engine (issue #324): youtubedl-android, the library Seal
    // uses — CPython + a lazy-extractors yt-dlp build as native .so files.
    // `:library` ONLY: the `:ffmpeg` artifact is 139 MB and would balloon
    // the release APK, so downloads use the best single-file format
    // (typically 720p for YouTube) — no video+audio merging. Seeded into
    // mavenLocal() with tools/seed-maven.py; GPL-3.0, see the downloader
    // class doc (no LICENSE file exists in this repo yet).
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    // Data layer dependency (Room persistence)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
