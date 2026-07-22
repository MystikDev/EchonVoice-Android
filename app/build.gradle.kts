import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase Cloud Messaging (push notifications) needs google-services.json from a
// Firebase project. Apply the plugin only when that file is present, so the repo
// still builds without it — push is simply inert until the file is dropped in.
// See distribution/PUSH-NOTIFICATIONS-SETUP.md.
if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.echon.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.echon.voice"
        minSdk = 24
        targetSdk = 35
        versionCode = 22
        versionName = "2.0.21"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing for direct (website) distribution. Secrets live in the
    // gitignored keystore.properties; when absent (e.g. CI without secrets) the
    // release build stays unsigned rather than failing configuration.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Two distribution builds of the same app (same applicationId):
    //  - direct: sideloaded from echon-voice.com, includes the in-app self-updater.
    //  - play:   Google Play (AAB), with the self-updater + install permissions
    //            stripped, because Play prohibits apps that update themselves.
    flavorDimensions += "distribution"
    productFlavors {
        create("direct") {
            dimension = "distribution"
            isDefault = true
        }
        create("play") {
            dimension = "distribution"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        // Enables java.time (and other newer APIs) on minSdk 24 via desugaring.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.security.crypto)

    // Firebase Cloud Messaging for push (message/DM) notifications.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // WorkManager backs the background self-updater, which only exists in `direct`.
    "directImplementation"(libs.androidx.work.runtime.ktx)

    implementation(libs.livekit.android)

    // CVE-2024-7254 (GHSA-735f-pc8j-v9w8): protobuf-javalite < 3.25.5 has a
    // parser DoS via deeply nested fields. LiveKit (still true of 2.27.0)
    // transitively declares 3.22.0; force it up to a patched release. Keep this
    // until a LiveKit release ships a safe protobuf on its own.
    constraints {
        implementation("com.google.protobuf:protobuf-javalite:3.25.5") {
            because("CVE-2024-7254: patch the transitive protobuf-javalite DoS")
        }
    }

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
