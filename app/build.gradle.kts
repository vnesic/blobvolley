plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "rs.vn.blobvolley"
    compileSdk = 35

    defaultConfig {
        applicationId = "rs.vn.blobvolley"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Priority: Environment variables > gradle.properties > local.properties
            storeFile = file(
                System.getenv("RELEASE_STORE_FILE")
                    ?: project.findProperty("RELEASE_STORE_FILE") as String?
                    ?: "keystore.jks"
            )
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                ?: project.findProperty("RELEASE_STORE_PASSWORD") as String?
                ?: ""
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                ?: project.findProperty("RELEASE_KEY_ALIAS") as String?
                ?: ""
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                ?: project.findProperty("RELEASE_KEY_PASSWORD") as String?
                ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
}
