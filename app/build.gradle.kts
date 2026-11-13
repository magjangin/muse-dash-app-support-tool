plugins {
    id("com.android.application")
}

android {
    namespace = "com.hwa.musedash.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hwa.musedash.assistant"
        minSdk = 26
        // targetSdk 35+는 edge-to-edge 강제 등 동작이 바뀐다. 사이드로드 전용 앱이라 올릴 이득이 없어 34로 둔다.
        targetSdk = 34
        versionCode = 7
        versionName = "2.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // v1.0.x는 uber-apk-signer에 들어 있는 공개 debug 키로 서명했다. 같은 키로 서명해야
        // 기존 사용자가 앱을 지우지 않고(설정·기록 초기화 예약 유지) 업데이트할 수 있다.
        create("legacy") {
            storeFile = rootProject.file("signing/uber-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("legacy")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("legacy")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // 앱 본체는 외부 라이브러리 없이 Android SDK만 쓴다 (APK 수십 KB 유지).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
