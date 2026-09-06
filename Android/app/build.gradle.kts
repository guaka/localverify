import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.mobiletriage.localverify"
    compileSdk = 34

    val releaseKeystore = System.getenv("LOCALVERIFY_RELEASE_KEYSTORE")
    val releaseStorePassword = System.getenv("LOCALVERIFY_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("LOCALVERIFY_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("LOCALVERIFY_RELEASE_KEY_PASSWORD")
    val hasReleaseSigning = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "org.mobiletriage.localverify"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-" + LocalDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    applicationVariants.all {
        if (buildType.name == "debug") {
            outputs.all {
                (this as BaseVariantOutputImpl).outputFileName = "localverify-debug.apk"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
    testImplementation("com.google.code.gson:gson:2.11.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
}

// Package only canonical JSON vectors, never diagnostic archives.
val contractFixtures by tasks.registering(Sync::class) {
    from(rootProject.file("../Fixtures")) { include("*.json") }
    into(layout.buildDirectory.dir("generated/contract-fixtures/fixtures"))
}
android.sourceSets.getByName("test").resources.srcDir(layout.buildDirectory.dir("generated/contract-fixtures"))
tasks.configureEach {
    if (name == "processDebugUnitTestJavaRes" || name == "processReleaseUnitTestJavaRes") dependsOn(contractFixtures)
}
