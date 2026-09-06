plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "org.localverify.sharedchecks"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.localverify.sharedchecks"
        minSdk = 30; targetSdk = 34; versionCode = 1; versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("androidTest").java.srcDir(rootProject.file("tests/kotlin"))
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.layout.buildDirectory.dir("test-resources"))
}
dependencies {
    implementation(project(":record-engine"))
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
tasks.configureEach { if (name.contains("AndroidTestAssets")) dependsOn(":record-engine:testResources") }
