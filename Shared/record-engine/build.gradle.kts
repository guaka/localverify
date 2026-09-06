plugins { kotlin("multiplatform"); kotlin("plugin.serialization") }
kotlin {
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "RecordEngine"; isStatic = true }
    }
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("jvmTest").kotlin.srcDir(rootProject.file("tests/kotlin"))
        getByName("jvmTest").resources.srcDir(rootProject.layout.buildDirectory.dir("test-resources"))
    }
}
val testResources by tasks.registering(Sync::class) {
    from(rootProject.file("../Fixtures")) { include("*.json"); into("fixtures") }
    from(rootProject.file("ThreatData")) { into("threat-data") }
    into(rootProject.layout.buildDirectory.dir("test-resources"))
}
tasks.matching { it.name == "jvmTestProcessResources" }.configureEach { dependsOn(testResources) }

val offlinePolicy by tasks.registering(Exec::class) {
    commandLine("python3", rootProject.file("../tools/check_shared_offline.py"))
}
tasks.configureEach { if (name.startsWith("compileKotlin") || name.startsWith("link")) dependsOn(offlinePolicy) }
