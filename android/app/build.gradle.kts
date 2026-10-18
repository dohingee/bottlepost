plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

android {
    // Change to your own reverse domain before the first Play upload; it can never change afterwards.
    namespace = "com.bottlepost.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bottlepost.app"
        minSdk = 26
        targetSdk = 36
        // CI passes the workflow run number so every uploaded bundle has a higher code.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    // Upload key for Play (Play App Signing re-signs for users). Only set in the release workflow;
    // local release builds stay unsigned.
    val uploadKeystore = System.getenv("UPLOAD_KEYSTORE")
    if (uploadKeystore != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // AdMob: debug builds always use Google's sample IDs, so testing never touches the real
        // account (clicking your own live ads can get it suspended).
        debug {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = "ca-app-pub-7472495816861016~6125650483"
            buildConfigField("String", "ADMOB_INTERSTITIAL", "\"ca-app-pub-7472495816861016/7063327631\"")
            isMinifyEnabled = true
            if (uploadKeystore != null) signingConfig = signingConfigs.getByName("upload")
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    androidResources {
        // Builds the per-app language list (Settings > Apps > Language) from values-* folders.
        generateLocaleConfig = true
        // Ship only the UI languages we translate; drops library strings for ~80 other locales.
        localeFilters += listOf("en", "ko", "ja", "es", "pt", "in", "vi", "th")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.config)

    // Pre-send interstitial (ads/AdMobAdGate.kt) and the consent message it needs first.
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.coroutines.play.services)
    implementation(libs.serialization.json)

    testImplementation(libs.junit)
}
