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
        versionCode = 14
        versionName = "0.1.0-m13"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            providers.environmentVariable("AI_LIVESAVER_KEYSTORE_PATH").orNull?.let { path ->
                signingConfig = signingConfigs.create("testing") {
                    storeFile = file(path)
                    storePassword = providers.environmentVariable("AI_LIVESAVER_STORE_PASSWORD").get()
                    keyAlias = providers.environmentVariable("AI_LIVESAVER_KEY_ALIAS").get()
                    keyPassword = providers.environmentVariable("AI_LIVESAVER_KEY_PASSWORD").get()
                }
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260719")
}
