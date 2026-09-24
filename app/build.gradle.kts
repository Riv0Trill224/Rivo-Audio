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

        versionCode = 1
        versionName = "0.1-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val media3Version = "1.11.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
}