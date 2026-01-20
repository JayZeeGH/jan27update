pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Optional JetBrains MediaPipe repo
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/mediapipe/maven") }
    }
}

rootProject.name = "APPLITA-ALPHABET-"
include(":app")
