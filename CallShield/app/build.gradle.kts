plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Your own signing key (set by the GitHub build from the KEYSTORE_BASE64 / KEYSTORE_PASSWORD secrets).
// Without it the APK falls back to Android's shared debug key, which Samsung and Play Protect distrust.
val ownKeystore: String? = System.getenv("CALLSHIELD_KEYSTORE_FILE")
val ownKeyPassword: String? = System.getenv("CALLSHIELD_KEYSTORE_PASSWORD")
val hasOwnKey = ownKeystore?.let { file(it).exists() } == true && !ownKeyPassword.isNullOrEmpty()

android {
    namespace = "com.callshield.ng"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callshield.ng"
        minSdk = 29          // Android 10+ (call screening role)
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    buildFeatures {
        buildConfig = true
    }

    // Two editions are built every time:
    //  full - everything, including call-history features and hang-up from the notification
    //  lite - no call-log or hang-up permissions, for phones whose anti-phishing check blocks "full"
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "CALL_LOG_FEATURES", "true")
        }
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "CALL_LOG_FEATURES", "false")
        }
    }

    signingConfigs {
        create("own") {
            if (hasOwnKey) {
                storeFile = file(ownKeystore!!)
                storePassword = ownKeyPassword
                keyAlias = "callshield"
                keyPassword = ownKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasOwnKey) signingConfigs.getByName("own")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
