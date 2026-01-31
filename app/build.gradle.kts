plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.traceroute"
    compileSdk = 36
    androidResources {
        generateLocaleConfig = true
    }
    splits {
        abi {
            isEnable  = true
            reset()
            // Specifies a list of ABIs that Gradle should create APKs for
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            // Optional: generate an additional APK that contains all ABIs
            isUniversalApk = true
        }
    }
    defaultConfig {
        applicationId = "com.example.traceroute"
        minSdk = 36
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(files("libs/nettools.aar"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}