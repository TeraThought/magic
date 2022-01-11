![Maven](https://img.shields.io/maven-metadata/v?color=A97BFF&metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fterathought%2Fenchant%2Fmagic%2Fmaven-metadata.xml)

![Tada](https://user-images.githubusercontent.com/74328946/143792081-e7d93a4c-4ef5-4698-98f1-08d7bda85341.gif)
# Magic
Simple, lightweight, modular components and utilities to help conjure your app architecture. Built with Kotlin coroutines to provide flexible
asynchronous behavior.

Learn about magic's APIs in the [Overivew](docs/Overview.md)

## Installation
Magic is hosted on Maven Central, simply add the following line to your build.gradle.kts 
```kotlin
implementation("com.terathought.enchant:magic:1.0.0-alpha03")
```
Also, add the utilities from the sample app (see below) to your project in order to observe ViewModels
from Compose and SwiftUI clients.

## Sample

Want to see what magic looks like in a real project? The project includes a sample KMM project alongside
the library so simply building the project (or opening the sampleIosApp folder in Xcode) allows you
to run the sample app. Here are the important source files that power the demo:
- [SampleViewModel.kt](sampleShared/src/commonMain/kotlin/enchant/magic/sample/SampleViewModel.kt) - Demonstrates how to implement ViewModel states and StatusViewModel statuses
- [MainActivity.java](sampleAndroidApp/src/main/java/enchant/magic/sample/MainActivity.kt) - Includes required Android utilities and a sample UI using `SampleViewModel` 
- [iOSApp.swift](sampleIosApp/sampleIosApp/iOSApp.swift) - Includes required iOS utilities and a sample UI using `SampleViewModel`

