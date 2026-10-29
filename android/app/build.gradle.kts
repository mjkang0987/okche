plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// 저장소 맨 위 VERSION이 스토어 앱 버전(iOS와 같은 값).
val appVersion = rootProject.file("../VERSION").readText().trim()

android {
    namespace = "app.okche"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.okche"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 원본을 복사하지 않고 design/ 의 파일을 그대로 쓴다: 토큰, 글꼴(res/font), 글꼴 라이선스.
    sourceSets["main"].apply {
        kotlin.directories += "../../design/generated"
        res.directories += "../../design/res"
        assets.directories += "../../design/fonts"
    }

    buildTypes {
        release {
            // 아이콘 묶음(material-icons-extended)에서 쓰는 5개만 남기고 줄인다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

room {
    // 버전마다 스키마 JSON을 남겨 두고 마이그레이션 테스트에 쓴다(커밋 대상).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.viewmodel.compose)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.ui.test.manifest)
}
