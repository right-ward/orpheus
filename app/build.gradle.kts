plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.rightward.orpheus"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.rightward.orpheus"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
