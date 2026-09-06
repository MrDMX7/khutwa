plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

// The Play edition is signed on GitHub Actions with a keystore handed over
// through the environment. On-device builds have none of these set and fall
// back to the debug key — which is what keeps the personal install updatable
// in place (same signer as the app already on the phone).
val playKeystore: String? = System.getenv("KEYSTORE_PATH")

// compileSdk is 35 everywhere that matters (CI → Play). The phone's aapt2 is
// the Termux aarch64 build from the Android 13 toolchain and cannot parse the
// android-35 platform resources, so on-device builds set khutwa.compileSdk=34
// in ~/.gradle/gradle.properties. Nothing in the app uses an API 35 symbol.
val compileSdkOverride: Int = (project.findProperty("khutwa.compileSdk") as String?)?.toInt() ?: 35

android {
    namespace = "com.dmx.khutwa"
    compileSdk = compileSdkOverride

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "3.0.0-alpha1"
    }

    // One codebase, two editions. Everything in src/main is shared; the two
    // flavor source sets differ in exactly one file, RootFeatures.kt.
    flavorDimensions += "edition"
    productFlavors {
        create("personal") {
            dimension = "edition"
            // The id the user's phone already has — keeps years of history.
            applicationId = "com.dmx.khutwa"
            versionNameSuffix = "-personal"
        }
        create("play") {
            dimension = "edition"
            // Permanent on Google Play; no su, no root code compiled in.
            applicationId = "com.elyoxe.khutwa"
        }
    }

    signingConfigs {
        create("play") {
            if (playKeystore != null) {
                storeFile = file(playKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    buildTypes {
        release {
            // Shrinking only where the full toolchain exists (CI). On-device
            // builds skip R8 — it needs the desktop android.jar toolchain.
            isMinifyEnabled = playKeystore != null
            isShrinkResources = playKeystore != null
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (playKeystore != null) signingConfigs.getByName("play")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    testImplementation("junit:junit:4.13.2")
}
