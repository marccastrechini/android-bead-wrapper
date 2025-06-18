@file:Suppress("UnstableApiUsage")

import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    // ── versions come from gradle/libs.versions.toml ──
    alias(libs.plugins.android.application)   // id = "com.android.application"
    alias(libs.plugins.kotlin.android)        // id = "org.jetbrains.kotlin.android"
    alias(libs.plugins.hilt)                  // id = "com.google.dagger.hilt.android"
    alias(libs.plugins.ksp)                   // id = "com.google.devtools.ksp"

    id("kotlin-parcelize")                    // not in the catalog
    jacoco                                     // built-in
}

android {
    namespace  = "com.beadpay.wrapper"
    compileSdk = 35

    defaultConfig {
        applicationId             = "com.beadpay.wrapper"
        minSdk                    = 26
        targetSdk                 = 35
        versionCode               = 1
        versionName               = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AppAuth redirect scheme
        manifestPlaceholders["appAuthRedirectScheme"] = "beadwrapper"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true        // generates BuildConfig.java / .kt
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // new property names in AGP 8.10
            enableUnitTestCoverage    = true   // JUnit tests that run on the JVM
            enableAndroidTestCoverage = true   // connected / instrumentation tests

            buildConfigField("String", "USERNAME", "\"6786eaf564636a2a69bd1848@beadpay.io\"")
            buildConfigField("String", "PASSWORD", "\"Kfn!b7NC@$\"")
            buildConfigField("String", "MERCHANT_ID", "\"664c5e3b0517b0a8a6321c9a\"")
            buildConfigField("String", "TERMINAL_ID", "\"6786eaf564636a2a69bd1848\"")
            buildConfigField("String", "CLIENT_ID",  "\"bead-terminal\"")
            buildConfigField("String", "SCOPE",      "\"openid profile email\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ───── DI & annotation processing ─────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ───── OAuth / OIDC ─────
    implementation("net.openid:appauth:0.11.1")
    implementation("androidx.browser:browser:1.8.0") // bump to 1.8.0-beta02 if needed

    // ───── AndroidX core / UI ─────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ───── Networking & JSON ─────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.12")

    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // ───── Secure credential storage ─────
    implementation("androidx.security:security-crypto:1.1.0-beta01")

    // ───── Logging ─────
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ───── Unit tests ─────
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // ───── Instrumentation tests ─────
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

/**
 * Generates JaCoCo coverage for unit + instrumentation tests:
 *   ./gradlew jacocoDebugReport
 */
tasks.register<JacocoReport>("jacocoDebugReport") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")

    val fileFilter = listOf("**/di/**", "**/generated/**", "**/*Hilt*.*")
    val mainSrc    = "${project.projectDir}/src/main/java"

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
