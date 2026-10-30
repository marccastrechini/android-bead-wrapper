@file:Suppress("UnstableApiUsage")

import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.Properties

plugins {
    // ── versions come from gradle/libs.versions.toml ──
    alias(libs.plugins.android.application)   // id = "com.android.application"
    alias(libs.plugins.kotlin.android)        // id = "org.jetbrains.kotlin.android"
    alias(libs.plugins.hilt)                  // id = "com.google.dagger.hilt.android"
    alias(libs.plugins.ksp)                   // id = "com.google.devtools.ksp"

    id("kotlin-parcelize")                    // not in the catalog
    jacoco                                     // built-in
}

/**
 * Terminal credentials, read from `local.properties` (git-ignored) or the
 * matching environment variable, so secrets never land in version control.
 *
 * The API key is issued per terminal per environment, so BEAD_API_KEY,
 * TERMINAL_ID and MERCHANT_ID must all belong to the same terminal, or the
 * API answers 403. No defaults: missing config is caught by BeadConfig at
 * runtime rather than silently falling back to stale IDs.
 */
val localProps = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

/**
 * local.properties → environment variable → default.
 *
 * Whitespace, a trailing semicolon and surrounding quotes are stripped, so
 * `KEY=abc`, `KEY = abc` and `KEY = "abc";` (the form copied out of the
 * generated BuildConfig.java) all behave the same. The value is re-quoted
 * when it is written back into BuildConfig.
 */
fun beadConfig(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default)
        .trim()
        .removeSuffix(";")
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .trim()

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

        // Terminal identity + credentials for POST /payments/crypto.
        // Defined here (not per-build-type) so release builds compile too.
        buildConfigField("String", "BEAD_API_KEY", "\"${beadConfig("BEAD_API_KEY")}\"")
        buildConfigField("String", "MERCHANT_ID",  "\"${beadConfig("MERCHANT_ID")}\"")
        buildConfigField("String", "TERMINAL_ID",  "\"${beadConfig("TERMINAL_ID")}\"")

        // Sent as `redirectUrl` at payment creation. The hosted page renders its
        // close/exit control only when it has somewhere to send the shopper, and
        // for a native WebView host that somewhere is this custom scheme, which
        // PaymentWebViewActivity intercepts on navigation. Overridable so an
        // https redirect can be tried without editing this file.
        buildConfigField(
            "String",
            "REDIRECT_URL",
            "\"${beadConfig("REDIRECT_URL", "beadwrapper://callback")}\""
        )
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
