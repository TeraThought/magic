pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android") {
                useModule("com.android.tools.build:gradle:7.0.3")
            }
        }
    }
    plugins {
        id("com.android.library") version "7.0.3"
        kotlin("android") version "1.6.0"
    }
}
include(":magic")
includeBuild("convention-plugins")
rootProject.name = "magic"