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

// Issue #414: the release tag at build time ("v0.4.34"), so the APK
// knows its own release — the in-app update check compares the
// latest GitHub release tag against the installed version, and
// versionName "0.1.0-beta" on every build would make every check say
// an update is available. Falls back to the pre-#414 values when git
// is unavailable or there is no tag yet. The release flow tags
// BEFORE the minified build, so a release APK stamps its own tag,
// not the previous one; worktree/merged debug builds stamp the
// latest reachable tag, which is harmless (they are never the
// installed release).
val releaseTag: String = try {
    providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
    }.standardOutput.asText.get().trim().removePrefix("v")
        .takeIf { it.isNotEmpty() } ?: "0.1.0-beta"
} catch (_: Exception) {
    "0.1.0-beta"
}

// versionCode from the tag (major*10000 + minor*100 + patch):
// v0.4.34 -> 434, always increasing from the old constant 2, so the
// package installer treats each release as an upgrade. Non-tag
// builds keep 2.
val releaseVersionCode: Int = try {
    val parts = releaseTag.split(".")
    if (parts.size == 3) {
        parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
    } else {
        2
    }
} catch (_: Exception) {
    2
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
        versionCode = releaseVersionCode
        versionName = releaseTag

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
        // Native libs (mpv) are extracted at install time
        // (useLegacyPackaging). AGP 9 refuses
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
    // Data layer dependency (Room persistence)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Koin 4.2.2 dependency injection (migration Phase 1).
    // koin-android: startKoin + androidContext + module DSL.
    // koin-compose / koin-compose-viewmodel: koinInject / koinViewModel
    // for the ViewModel phase (Phase 2); declared here so the
    // dependency set is complete from the start.
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-compose:4.2.2")
    implementation("io.insert-koin:koin-compose-viewmodel:4.2.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
