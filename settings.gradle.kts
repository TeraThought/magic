pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:7.2.2")
            }
        }
    }
    plugins {
        id("com.android.library") version "7.0.3"
        kotlin("android") version "1.7.10"
    }
}
include(":magic", ":sample", ":sampleAndroidApp", ":sampleShared")
includeBuild("convention-plugins")
rootProject.name = "magic"
