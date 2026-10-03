plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.family.huafei"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.family.huafei"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // 签名密钥不随仓库分发:本地放置 huafei-release.jks 并在 gradle.properties
    // 配置 huafei.storePassword / huafei.keyPassword 后,assembleRelease 才会产出签名包,
    // 否则产出未签名 APK,日常调试不受影响。
    val releaseStoreFile = rootProject.file("huafei-release.jks")
    if (releaseStoreFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = findProperty("huafei.storePassword") as String?
                keyAlias = findProperty("huafei.keyAlias") as String? ?: "huafei"
                keyPassword = findProperty("huafei.keyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseStoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testImplementation("junit:junit:4.13.2")
}
