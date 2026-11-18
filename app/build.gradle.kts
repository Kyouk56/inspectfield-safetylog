import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing secrets are read from a gitignored local file, then from Gradle
// properties / environment (CI). Never hardcode or commit them.
val signingSecrets = Properties().apply {
    val secretsFile = rootProject.file("signing-secrets.properties")
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
fun signingSecret(name: String): String? =
    project.findProperty(name) as? String
        ?: System.getenv(name)
        ?: signingSecrets.getProperty(name)

android {
    namespace = "com.safetylog.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.safetylog.inspection.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "1.0.16"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val admobAppId = project.findProperty("SAFETYLOG_ADMOB_APP_ID") as? String
            ?: System.getenv("SAFETYLOG_ADMOB_APP_ID")
            ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["admobAppId"] = admobAppId

        val admobBannerId = project.findProperty("SAFETYLOG_ADMOB_BANNER_ID") as? String
            ?: System.getenv("SAFETYLOG_ADMOB_BANNER_ID")
            ?: "ca-app-pub-3940256099942544/6300978111"
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")

        // Developer/test controls (sandbox auth, role & paid-tier simulation) are
        // always on for debug builds. A release build can opt in for an internal
        // test of the production code path with -PenableTestControls=true.
        val enableTestControls = (project.findProperty("enableTestControls") as? String)?.toBoolean() ?: false
        buildConfigField("Boolean", "ENABLE_TEST_CONTROLS", enableTestControls.toString())

        // Server-authoritative authorization. Release builds must supply both values;
        // without them privileged actions fail closed (inspections remain fully offline).
        val apiBaseUrl = project.findProperty("AUTH_API_BASE_URL") as? String
            ?: System.getenv("AUTH_API_BASE_URL")
            ?: ""
        val authPublicKey = project.findProperty("AUTH_PUBLIC_KEY_SPKI_BASE64") as? String
            ?: System.getenv("AUTH_PUBLIC_KEY_SPKI_BASE64")
            ?: ""
        buildConfigField("String", "AUTH_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "AUTH_PUBLIC_KEY_SPKI_BASE64", "\"$authPublicKey\"")
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
            val keystorePass = signingSecret("SAFETYLOG_KEYSTORE_PASSWORD")
            val keyAliasStr = signingSecret("SAFETYLOG_KEY_ALIAS") ?: "safetylog_release"
            val keyPassStr = signingSecret("SAFETYLOG_KEY_PASSWORD")

            // A release build must never fall back to the public debug signing key.
            // Leaving storeFile null makes AGP fail the release build with a clear error;
            // debug builds are unaffected because they use their own signing config.
            storeFile = keystorePath?.let(::file)?.takeIf { it.exists() }
            storePassword = keystorePass.orEmpty()
            keyAlias = keyAliasStr
            keyPassword = keyPassStr.orEmpty()
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
            // Debug builds always have the test controls, regardless of the property.
            buildConfigField("Boolean", "ENABLE_TEST_CONTROLS", "true")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose & Material 3 (versions managed by the Compose BOM)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image Loading
    implementation(libs.coil.compose)

    // In-App Purchases
    implementation(libs.androidx.billing.ktx)

    // Android Core & File Handling
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)

    // Android Security & Cryptography (AES-256 GCM backed by Android Keystore MasterKey)
    implementation(libs.androidx.security.crypto)

    // Google Credential Manager & OAuth Identity
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Google Mobile Ads (AdMob) & Multi-Network Mediation Adapters (100% Free Developer SDKs)
    implementation(libs.play.services.ads)
    implementation(libs.mediation.facebook)
    implementation(libs.mediation.unity)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
