plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kirvy.stepwaylauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kirvy.stepwaylauncher"
        minSdk = 24
        targetSdk = 31
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmado con la llave debug para que se pueda instalar directo desde USB.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // No se publica en Play Store: no detener la compilación por reglas de la tienda.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
