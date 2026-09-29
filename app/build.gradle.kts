plugins {
    id("com.android.application")
}

android {
    namespace = "com.riv0trill.rivoaudio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riv0trill.rivoaudio"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 36

        versionCode = 3
        versionName = "0.2.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.media3:media3-ui:1.11.1")
    val media3Version = "1.11.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
}