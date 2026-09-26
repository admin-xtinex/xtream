plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.xtream.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.xtream.tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 20
        versionName = "2.7.1"
    }

    flavorDimensions += "device"
    productFlavors {
        create("tv") {
            dimension = "device"
            applicationId = "app.xtream.tv"
        }
        create("mobile") {
            dimension = "device"
            applicationId = "app.xtream.mobile"
        }
    }

    signingConfigs {
        create("sideload") {
            storeFile = file("xtream-sideload.jks")
            storePassword = "xtream-sideload"
            keyAlias = "xtream"
            keyPassword = "xtream-sideload"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
