import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    // NOTE: com.google.gms.google-services is deliberately NOT applied. Firebase
    // is optional; see README "Enabling real push". The app builds and runs with
    // no google-services.json present.
}

android {
    namespace = "app.pact.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pact.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Relay base URL. Default targets the host loopback as seen from the
        // Android emulator (10.0.2.2 == host's 127.0.0.1). Override per build
        // type or via a local.properties "pact.relayBaseUrl" entry.
        val relayBaseUrl = readLocalProperty("pact.relayBaseUrl") ?: "http://10.0.2.2:8787"
        buildConfigField("String", "RELAY_BASE_URL", "\"$relayBaseUrl\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Keep R8 off by default so a headless assembleRelease succeeds without
            // a tuned keep-rule set. Turn on for a real Play release (see README).
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // Compiler extension compatible with Kotlin 1.9.24.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)

    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    // Firebase messaging via BoM. Guarded at runtime; no google-services plugin.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}

/** Reads a key from local.properties if the file exists; returns null otherwise. */
fun readLocalProperty(key: String): String? {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return null
    val props = Properties()
    f.inputStream().use { props.load(it) }
    return props.getProperty(key)
}
