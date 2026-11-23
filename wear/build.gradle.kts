import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Same optional signing as :app, from the same keystore.properties or SUSTENANCE_* env vars.
// The watch and phone APKs must be signed with one key: the Data Layer only connects installs
// of the same package *and* signature, so a mismatched pair silently never syncs.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

val hasSigning = signingValue("storeFile", "SUSTENANCE_STORE_FILE") != null

android {
    namespace = "xyz.draumaz.sustenance.wear"
    compileSdk = 37

    defaultConfig {
        // Must match the phone app's applicationId (and signing key): the Data Layer only
        // delivers items between installs of the same package.
        applicationId = "xyz.draumaz.sustenance"
        minSdk = 30
        targetSdk = 36
        // Keep in step with :app's version when releasing.
        versionCode = 14
        versionName = "1.9.4"
    }

    androidResources {
        localeFilters += listOf("en", "ru")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingValue("storeFile", "SUSTENANCE_STORE_FILE")!!)
                storePassword = signingValue("storePassword", "SUSTENANCE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SUSTENANCE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SUSTENANCE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)

    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.guava)
}
