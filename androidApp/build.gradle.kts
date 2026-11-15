import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }

// Release builds get their version from CI (.github/workflows/release.yml): the name is the
// GitHub release's name and the code is the release's publish time as a Unix timestamp. Local
// builds without them are "dev" / 1.
val versionNameEnv = providers.environmentVariable("VERSION_NAME").map { it.trim() }.filter { it.isNotEmpty() }
val versionCodeEnv = providers.environmentVariable("VERSION_CODE").map { it.trim().toInt() }

// Release signing, also from CI (org secrets). Without a keystore the release APK is left unsigned.
val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")

android {
    namespace = "dev.fanchao.mycardbalance.android"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "dev.fanchao.mycardbalance"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionCodeEnv.orElse(1).get()
        versionName = versionNameEnv.orElse("dev").get()
    }

    signingConfigs {
        if (keystorePath.isPresent) {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to the released app on the same phone.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.uiTooling)
}
