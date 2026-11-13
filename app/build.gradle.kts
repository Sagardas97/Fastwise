import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Your personal signing key is not in the repository. GitHub Actions restores it from the repository
// secrets FASTWISE_KEYSTORE_BASE64 and FASTWISE_KEYSTORE_PASSWORD; on your PC, a keystore.properties
// file (never committed) points at your copy. See the README.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun signingSetting(property: String, env: String): String? =
    keystoreProps.getProperty(property)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val keystorePath = signingSetting("storeFile", "FASTWISE_KEYSTORE_PATH")
val keystorePassword = signingSetting("storePassword", "FASTWISE_KEYSTORE_PASSWORD")

android {
    namespace = "app.fastwise"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.fastwise"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // One personal signing key for every build (your PC or GitHub Actions),
    // so a newer APK installs over the old one and your data is kept.
    // Without the key (for example in someone else's copy), builds use Android's debug key instead.
    signingConfigs {
        if (keystorePath != null && keystorePassword != null) {
            create("personal") {
                storeFile = rootProject.file(keystorePath)
                storeType = "pkcs12"
                storePassword = keystorePassword
                keyAlias = signingSetting("keyAlias", "FASTWISE_KEY_ALIAS") ?: "fastwise"
                keyPassword = keystorePassword
            }
        }
    }
    val signing = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")

    buildTypes {
        getByName("debug") {
            signingConfig = signing
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
}
