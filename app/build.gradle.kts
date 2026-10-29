plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.safetylog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safetylog.inspection.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val admobAppId = project.findProperty("SAFETYLOG_ADMOB_APP_ID") as? String
            ?: System.getenv("SAFETYLOG_ADMOB_APP_ID")
            ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["admobAppId"] = admobAppId
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val defaultKeystore = file("keystore/safetylog-release.jks")
            val keystorePath = project.findProperty("SAFETYLOG_KEYSTORE_FILE") as? String
                ?: System.getenv("SAFETYLOG_KEYSTORE_FILE")
                ?: if (defaultKeystore.exists()) defaultKeystore.absolutePath else null
            val keystorePass = project.findProperty("SAFETYLOG_KEYSTORE_PASSWORD") as? String
                ?: System.getenv("SAFETYLOG_KEYSTORE_PASSWORD")
                ?: "safetylogPass2026"
            val keyAliasStr = project.findProperty("SAFETYLOG_KEY_ALIAS") as? String
                ?: System.getenv("SAFETYLOG_KEY_ALIAS")
                ?: "safetylog-release-key"
            val keyPassStr = project.findProperty("SAFETYLOG_KEY_PASSWORD") as? String
                ?: System.getenv("SAFETYLOG_KEY_PASSWORD")
                ?: "safetylogPass2026"

            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = keyAliasStr
                keyPassword = keyPassStr
            } else {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    // Room Database
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // In-App Purchases
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    // Android Core & File Handling
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Android Security & Cryptography (AES-256 GCM backed by Android Keystore MasterKey)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Credential Manager & OAuth Identity
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Mobile Ads (AdMob) for Hybrid Banner
    implementation("com.google.android.gms:play-services-ads:23.3.0")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
