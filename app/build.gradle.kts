import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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

    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
        }
        create("fdroid") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("test") {
            java.directories.add("src/testFixtures/java")
            kotlin.directories.add("src/testFixtures/java")
        }
        getByName("androidTest") {
            java.directories.add("src/testFixtures/java")
            kotlin.directories.add("src/testFixtures/java")
        }
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
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    composeCompiler {
        includeComposeMappingFile.set(false)
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
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ViewModel + viewModelScope for the Phase 2 ViewModels.
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media)
    // MPV engine dependency (Issue #52)
    implementation(libs.mpv.android.lib) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "androidx.appcompat")
    }
    // Haze 2.0 visual effects (M2.5 UI revolution)
    implementation(libs.haze)
    // Data layer dependency (Room persistence)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Koin 4.2.2 dependency injection (migration Phase 1).
    // koin-android: startKoin + androidContext + module DSL.
    // koin-compose / koin-compose-viewmodel: koinInject / koinViewModel
    // for the ViewModel phase (Phase 2); declared here so the
    // dependency set is complete from the start.
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Unit tests (migration Phase 2a — first test source set in the repo).
    // ViewModels are tested with fakes, no mocking framework.
    testImplementation(libs.junit)
    // kotlinx-coroutines-test aligned to the coroutines-core version
    // the project resolves (1.10.2, seeded in the local Maven repo).
    testImplementation(libs.kotlinx.coroutines.test)
}
