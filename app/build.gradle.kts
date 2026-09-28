plugins {
    id("com.android.application")
}

android {
    namespace = "com.riv0trill.rivoaudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riv0trill.rivoaudio"

        minSdk = 26
        targetSdk = 36

        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-ui:1.11.1")
    val media3Version = "1.11.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
}