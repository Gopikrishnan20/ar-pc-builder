import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so there is no kotlin-android plugin here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.arpcbuilder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arpcbuilder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0"

        // ARCore phones are ARM; dropping x86 native code keeps the APK small.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // glb files are already compressed; keep them stored so Filament can map them directly.
    androidResources {
        noCompress += listOf("glb", "gltf", "hdr", "ktx")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose + Material 3
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // AR: SceneView (Filament + ARCore). ARCore is declared explicitly to pin its version.
    implementation(libs.sceneview.ar)
    implementation(libs.arcore)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Tests: the domain layer is pure Kotlin, so plain JVM unit tests are enough.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
