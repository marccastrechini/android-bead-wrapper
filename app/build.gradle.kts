@file:Suppress("UnstableApiUsage")              // for isTestCoverageEnabled

import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
    id("jacoco")                                // ← NEW
}

android {
    namespace = "com.beadpay.wrapper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.beadpay.wrapper"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "beadwrapper"
    }

    buildFeatures { viewBinding = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isTestCoverageEnabled = true        // enables JaCoCo .ec files
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("net.openid:appauth:0.11.1")
    implementation("androidx.browser:browser:1.8.0")

    // AndroidX core UI
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Networking & parsing
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.12")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Secure credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Dependency injection (Hilt)
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-android-compiler:2.51")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ---------- Unit tests ----------
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // ---------- Instrumentation tests ----------
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}

/**
 * Generates JaCoCo coverage for unit + instrumentation tests:
 *   ./gradlew jacocoDebugReport
 */
tasks.register<JacocoReport>("jacocoDebugReport") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")

    val fileFilter = listOf("**/di/**", "**/generated/**", "**/*Hilt*.*")
    val mainSrc = "${project.projectDir}/src/main/java"

    classDirectories.setFrom(
        fileTree("${buildDir}/intermediates/classes/debug") { exclude(fileFilter) }
    )
    sourceDirectories.setFrom(files(mainSrc))
    executionData.setFrom(fileTree(buildDir) { include("**/*.ec") })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
