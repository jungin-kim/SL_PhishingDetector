import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        // 이미 gradle.properties에 추가했다면 생략 가능하지만,
        // 여기서 한 번 더 지정해주면 확실히 전달됩니다.
        freeCompilerArgs += listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED"
        )
    }
}

plugins {
    kotlin("jvm") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
    kotlin("kapt") version "1.9.24" apply false

    id("com.android.application") version "8.1.1" apply false
    id("com.android.library") version "8.1.1" apply false
}


buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // Android Gradle Plugin 버전은 8.0.0 이상으로 맞추는 걸 추천합니다.
        classpath("com.android.tools.build:gradle:8.1.1")
        // Kotlin Gradle Plugin 버전도 적어 줍니다.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
    }
}

