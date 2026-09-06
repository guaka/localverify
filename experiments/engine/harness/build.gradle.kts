plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "org.localverify.experiment"
    compileSdk = 34
    defaultConfig {
        applicationId = "org.localverify.experiment"
        minSdk = 30; targetSdk = 34; versionCode = 1; versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    flavorDimensions += "engine"
    productFlavors {
        create("rust") { dimension = "engine"; applicationIdSuffix = ".rust" }
        create("kmp") { dimension = "engine"; applicationIdSuffix = ".kmp" }
        create("baseline") { dimension = "engine"; applicationIdSuffix = ".baseline" }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("rust").java.srcDir("../build/bindings/uniffi")
    sourceSets.getByName("rust").jniLibs.srcDir("../build/jniLibs")
    sourceSets.getByName("androidTest").assets.srcDir("../build/fixtures")
}
dependencies {
    "rustImplementation"("net.java.dev.jna:jna:5.17.0@aar")
    "kmpImplementation"(project(":kmp"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
val fixtures by tasks.registering(Sync::class) {
    from("../../../Fixtures") { include("*.json") }
    into("../build/fixtures")
}
tasks.configureEach { if (name.contains("AndroidTestAssets")) dependsOn(fixtures) }
android.sourceSets.getByName("androidTestRust").java.srcDir("src/candidateTest/java")
android.sourceSets.getByName("androidTestKmp").java.srcDir("src/candidateTest/java")
// Keep size measurements independent of whether a host NDK happens to be installed.
android.packaging.jniLibs.keepDebugSymbols += "**/*.so"
