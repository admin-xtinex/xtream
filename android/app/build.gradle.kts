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
        versionCode = 11
        versionName = "2.0.0"
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

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-omni:140.0.20250707120347")
    testImplementation("junit:junit:4.13.2")
}
