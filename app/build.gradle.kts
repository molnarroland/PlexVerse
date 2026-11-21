import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Ships a compiled profile in the APK, so focus moves are fast from the first start. See baselineprofile/.
    id("androidx.baselineprofile")
    id("com.android.compose.screenshot")
}

// The release key lives outside the repo (~/.plexverse-release, backed up by its owner). Without it,
// release builds fall back to the debug key, which can't update a TV that has a real release on it.
val releaseKey = File(System.getProperty("user.home"), ".plexverse-release/keystore.properties")
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

// Every commit is a newer version, so the in-app update check can compare numbers.
val commitCount = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.get().trim().toIntOrNull() ?: 1

// The version name comes from the latest git tag: "0.1.0" on the tagged commit, "0.1.0-3-gabc1234"
// three commits after it, with "-dirty" when there are uncommitted changes.
val gitVersion = providers.exec {
    commandLine("git", "describe", "--tags", "--dirty")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { "0.0.0" }

android {
    namespace = "hu.molnarroland.plexverse"
    compileSdk = 37

    defaultConfig {
        applicationId = "hu.molnarroland.plexverse"
        minSdk = 26
        targetSdk = 37
        versionCode = commitCount
        versionName = gitVersion
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = File(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // The build that goes on the TV. Debug builds run Compose unoptimized, which makes focus
        // moves and scrolling stutter on the TV's slow CPU.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // The Plex connection, the player and the cards (simplex-core, from github.com/embergabor/simplex-maven);
    // brings Compose, Media3, Coil and OkHttp.
    implementation("com.embergabor:simplex-core:0.1.2")
    // The system splash on Android 12+, handing over to the app's own animated one.
    implementation("androidx.core:core-splashscreen:1.0.1")
    // The TV's home screen: Continue watching (Watch Next) and the PlexVerse channel.
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    // The QR code on the sign-in screen.
    implementation("com.google.zxing:core:3.5.3")
    // @Preview for every screen (see ui/preview), with the Compose version simplex-core brings.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))

    testImplementation("junit:junit:4.13.2")
    // The real org.json, in place of the Android stubs, for tests that parse Plex responses.
    testImplementation("org.json:json:20240303")
}
