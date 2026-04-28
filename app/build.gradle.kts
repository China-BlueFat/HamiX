import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val defaultBuildDate = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
val buildCount = providers.gradleProperty("BUILD_COUNT").orNull
    ?: System.getenv("BUILD_COUNT")
    ?: "01"
val buildStamp = providers.gradleProperty("BUILD_STAMP").orNull
    ?: System.getenv("BUILD_STAMP")
    ?: "$defaultBuildDate$buildCount"
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH") ?: "hamix-release.jks"
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: "960412"
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "hamix"
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: "960412"

android {
    namespace = "com.zayne.hamix"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zayne.hamix"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    signingConfigs {
        create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.foundation)
    implementation(libs.material)
    implementation("com.github.equationl.paddleocr4android:ncnnandroidppocr:v1.3.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-shapes-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.0")

    implementation("io.github.kyant0:backdrop:1.0.6")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register<Copy>("renameDebugApk") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug"))
    include("*.apk")
    into(layout.buildDirectory.dir("outputs/renamed/debug"))
    rename { "HamiX-$buildStamp-Debug.apk" }
}

tasks.register<Copy>("renameReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(layout.buildDirectory.dir("outputs/renamed/release"))
    rename { "HamiX-$buildStamp-Release.apk" }
}
