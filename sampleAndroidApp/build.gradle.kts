plugins {
    id("com.android.application")
    kotlin("android")
}

val compose_version = "1.1.0-rc01"
val kotlin_version = "1.6.0"

android {
    compileSdk = 32
    defaultConfig {
        applicationId = "enchant.magic.sample.android"
        minSdk = 21
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = compose_version
    }
}

dependencies {
    implementation(project(":sampleShared"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")

    implementation("androidx.compose.ui:ui:$compose_version")
    implementation("androidx.compose.material:material:$compose_version")
    implementation("androidx.compose.ui:ui-tooling:$compose_version")
    implementation("androidx.activity:activity-compose:1.4.0")

    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.activity:activity-ktx:1.4.0")
}