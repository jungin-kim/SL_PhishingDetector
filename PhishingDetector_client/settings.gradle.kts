// ── PhishingDetector/settings.gradle.kts ────────────────────────────────────
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    plugins {
        // Kotlin Gradle Plugin 1.9.0 을 프로젝트 전체에서 사용하겠다는 선언
        kotlin("android") version "1.9.0" apply false
        kotlin("kapt")    version "1.9.0" apply false

        // Android Gradle Plugin 버전 (Compose, Room 호환 8.1.0 이상 권장)
        id("com.android.application") version "8.1.0" apply false
        id("com.android.library")    version "8.1.0" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PhishingDetector"
include(":app")
