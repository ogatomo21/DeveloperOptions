import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.ogatomo.developerOptions"
    // Android 17
    compileSdk = 37

    defaultConfig {
        applicationId = "net.ogatomo.developerOptions"
        // Shizuku API 13+ が minSdk 24。TileService も API 24+
        minSdk = 24
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ローカル: root/local.properties の storeFile 等
    // CI: app/keystore.jks + 環境変数 KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    signingConfigs {
        create("release") {
            val localProps = rootProject.file("local.properties")
            if (localProps.exists()) {
                val props = Properties().apply { load(localProps.inputStream()) }
                val storePath = props["storeFile"] as? String
                if (!storePath.isNullOrBlank()) {
                    storeFile = rootProject.file(storePath)
                }
                storePassword = props["storePassword"] as? String ?: ""
                keyAlias = props["keyAlias"] as? String ?: ""
                keyPassword = props["keyPassword"] as? String ?: ""
            } else {
                storeFile = file("keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.appcompat)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)
}