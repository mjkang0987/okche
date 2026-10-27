plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
    debugImplementation(libs.compose.ui.test.manifest)
}
