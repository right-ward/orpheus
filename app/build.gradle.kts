plugins {
    id("com.android.application")
}

val ciKeystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
val ciKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val ciKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val ciKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasCiSigning = listOf(
    ciKeystoreFile,
    ciKeystorePassword,
    ciKeyAlias,
    ciKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.rightward.orpheus"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rightward.orpheus"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.0"
    }

    signingConfigs {
        if (hasCiSigning) {
            create("ciRelease") {
                storeFile = file(ciKeystoreFile!!)
                storePassword = ciKeystorePassword!!
                keyAlias = ciKeyAlias!!
                keyPassword = ciKeyPassword!!
            }
            create("ciDebug") {
                storeFile = file(ciKeystoreFile!!)
                storePassword = ciKeystorePassword!!
                keyAlias = ciKeyAlias!!
                keyPassword = ciKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasCiSigning) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
        debug {
            isMinifyEnabled = false
            if (hasCiSigning) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
