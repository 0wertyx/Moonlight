plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.moonlight.school"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.moonlight.school"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.1"
    }
    buildFeatures { compose = true; buildConfig = true }
    val persistentKey = System.getenv("MOONLIGHT_KEYSTORE_PATH")
    val persistentPassword = System.getenv("MOONLIGHT_KEYSTORE_PASSWORD")
    if (!persistentKey.isNullOrBlank() && !persistentPassword.isNullOrBlank()) {
        val persistentSigning = signingConfigs.create("moonlightPersistent") {
            storeFile = file(persistentKey)
            storePassword = persistentPassword
            keyAlias = "moonlight"
            keyPassword = persistentPassword
        }
        buildTypes.getByName("debug").signingConfig = persistentSigning
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
