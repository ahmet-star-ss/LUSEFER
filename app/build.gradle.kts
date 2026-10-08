plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.sadrazam.lusifer"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.sadrazam.lusifer"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Sabit imza: her derlemede aynı anahtar -> APK'yı silmeden üstüne güncellersin
    signingConfigs {
        create("sabit") {
            storeFile = file("../lusifer.keystore")
            storePassword = "lusifer123"
            keyAlias = "lusifer"
            keyPassword = "lusifer123"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sabit") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sabit")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // Gömülü modeller sıkıştırılmaz (llama.cpp / Vosk doğrudan okur)
    androidResources { noCompress += listOf("gguf", "onnx", "bin", "mdl", "fst", "conf") }
    packaging { jniLibs { useLegacyPackaging = true } }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Offline STT / uyandırma kelimesi
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Yetki katmanı (Shizuku)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
