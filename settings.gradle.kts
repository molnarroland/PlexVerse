pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // simplex-core (the Plex connection and the player), only as published to simplex-maven.
        maven("https://raw.githubusercontent.com/embergabor/simplex-maven/main") {
            content { includeGroup("com.embergabor") }
        }
    }
}
rootProject.name = "PlexVerse"
include(":app")
include(":baselineprofile")
