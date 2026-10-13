import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.musicsm"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.musicsm"
        minSdk = 24
        targetSdk = 37
        versionCode = 11
        versionName = "4.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 was crashing the release build at runtime; disabled until the exact keep
            // rule is confirmed against a device crash log. Re-enable once verified.
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Plenty of the code under test touches android.util.Log and android.net.Uri, which
            // throw "not mocked" on the JVM unless stubbed methods return defaults instead.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Room writes the schema of every version here. Committing these lets Room verify migrations
// in tests and gives a reviewable diff whenever the database shape changes.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Media3 playback
    implementation(libs.androidx.media3.exoplayer)
    // Motion covers are served as HLS, which the core exoplayer artifact cannot read on its own.
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Glassmorphism (backdrop blur)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.backdrop)

    // Coroutines, palette, http, extractor
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.okhttp)
    implementation(libs.newpipe.extractor)

    // YouTube Music metadata: real albums, artists and the related-track graph.
    implementation(project(":innertube"))

    // Looping cover videos for tracks whose release ships one.
    implementation(project(":motionart"))

    // QR codes for playlist sharing
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)

    // Wear OS companion (Data Layer)
    implementation(libs.play.services.wearable)

    // Java 8+ API desugaring (NewPipeExtractor uses java.nio.file; minSdk 24)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
