plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.lzyuuu.ailivesaver"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.lzyuuu.ailivesaver"
        minSdk = 28
        targetSdk = 36
        versionCode = 112
        versionName = "0.1.0-m112"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "io.github.lzyuuu.ailivesaver.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val keystorePath = providers.environmentVariable("AI_LIVESAVER_KEYSTORE_PATH").orNull
            signingConfig = if (keystorePath != null) {
                signingConfigs.create("testing") {
                    storeFile = file(keystorePath)
                    storePassword = providers.environmentVariable("AI_LIVESAVER_STORE_PASSWORD").get()
                    keyAlias = providers.environmentVariable("AI_LIVESAVER_KEY_ALIAS").get()
                    keyPassword = providers.environmentVariable("AI_LIVESAVER_KEY_PASSWORD").get()
                }
            } else {
                // 测试版默认复用 debug 证书，避免 assembleRelease 生成无法安装的 unsigned APK。
                signingConfigs.getByName("debug")
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
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // ggml 运行时按目录 dlopen CPU 后端变体（libggml-cpu-android_*.so），
        // 必须解压安装 jniLibs（targetSdk 36 默认从 APK 内映射，路径不可用）。
        jniLibs.useLegacyPackaging = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")

    // LiteRT-LM 本地推理（官方 Google Maven：0.0.0-alpha05）。AAR minSdk 31，
    // 主应用仍是 28，靠 manifest tools:overrideLibrary 合并。
    implementation("com.google.ai.edge.litertlm:litertlm:0.0.0-alpha05")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
