import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.musicsm.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Must match the phone app: the Wearable Data Layer only pairs apps that share an
        // application id and signing key.
        applicationId = "com.example.musicsm"
        minSdk = 30
        targetSdk = 37
        versionCode = 5
        versionName = "4.0.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Wear UI + phone connectivity
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.androidx.wear) // Ambient (always-on display) support
    implementation(libs.play.services.wearable)

    // Album art: loaded by URL over the network (the watch has its own connectivity).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Extracts the accent colour from the current cover, matching the phone app's artwork theming.
    implementation(libs.androidx.palette.ktx)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
