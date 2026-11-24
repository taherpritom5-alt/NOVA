plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.pritom.nova"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pritom.nova"
        minSdk = 26
        targetSdk = 34
        versionCode = run
        versionName = "1.$run"
    }
    signingConfigs {
        create("nova") {
            storeFile = file("nova.keystore")
            storePassword = "novanova"
            keyAlias = "nova"
            keyPassword = "novanova"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nova")
        }
        debug { signingConfig = signingConfigs.getByName("nova") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
