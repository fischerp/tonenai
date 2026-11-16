plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// The starter library: the songs that ship with the repository (songs/ files tracked by Git) are packed into the APK
// as assets/songs/. Songs you created yourself are not tracked, so they are not packed: they reach the phone
// through the Google Drive sync.
val repoDir = rootProject.file("..")
val starterDir = layout.buildDirectory.dir("starter-songs")
val starterSongs = tasks.register<Sync>("starterSongs") {
    val tracked: List<String> = runCatching {
        providers.exec { commandLine("git", "ls-files", "songs"); workingDir = repoDir; isIgnoreExitValue = true }
            .standardOutput.asText.get().lines().map { it.trim() }
            .filter { it.endsWith(".json") || it.endsWith(".tsl") }
    }.getOrDefault(emptyList())
    from(repoDir) { include(tracked.ifEmpty { listOf("songs/*.json", "songs/*.tsl") }); include("gear/catalog.json", "assets/tonenai-intro.wav") }   // no Git: take the folder
    into(starterDir)
}
tasks.named("preBuild") { dependsOn(starterSongs) }

android {
    namespace = "com.tonenai.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tonenai.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2"
    }
    sourceSets["main"].assets.srcDir(starterDir.get().asFile)
    buildTypes {
        release { isMinifyEnabled = false }
    }
    androidResources { noCompress += "wav" }      // the opening sound is played straight from the APK

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Google Identity Services: authorization for the optional Drive sync (drive.appdata scope only).
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    // Guitar pictures: SVG files pre-rendered from the desktop artwork (tools/render_guitar_svgs.js), drawn by AndroidSVG (pure Java).
    implementation("com.caverock:androidsvg-aar:1.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // android.jar's org.json is a stub in local unit tests
}
