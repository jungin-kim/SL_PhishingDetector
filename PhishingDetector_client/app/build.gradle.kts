plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")   // Room 등 어노테이션 프로세서 사용 시 필요
}

android {
    namespace = "com.example.phishingdetector" // 실제 패키지명으로 바꿔주세요
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.phishingdetector" // 실제 패키지명으로 바꿔주세요
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = false
        viewBinding = true
    }
    composeOptions {
        // Compose Compiler Extension 버전 (Compose 버전에 맞춰 업데이트하세요)
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            // 리소스 충돌 방지를 위한 기본 설정
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // (선택) 또는 추가 빌드 타입/프로가드/릴리즈 설정이 필요하다면 여기에 작성
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")

    // Compose (Compose BOM으로 맞추기)
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.androidx.material3.android)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Room (정상 작동 중)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // PyTorch Lite
    implementation("org.pytorch:pytorch_android_lite:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.1")

    // OkHttp, Jsoup
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("org.jsoup:jsoup:1.16.1")

    // SentencePiece 대체 (smile-nlp)
    implementation("com.github.haifengl:smile-nlp:3.0.0")

    // Coroutine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation ("com.google.android.material:material:1.9.0")
    implementation ("androidx.cardview:cardview:1.0.0")
}

kapt {
    // Room이 생성하는 코드를 더 확실하게 보여 주도록
    correctErrorTypes = true
}