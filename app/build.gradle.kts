plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Present on a machine configured to sign uploads (see ~/.gradle/gradle.properties).
// Absent on CI and on fresh clones, where only debug/test builds are expected to work.
val hasUploadKeystore = providers.gradleProperty("STOCHASTIC_UPLOAD_STORE_FILE").isPresent

android {
    namespace = "io.github.fowles.stochastic_strength"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.fowles.stochastic_strength"
        minSdk = 33
        targetSdk = 36
        versionCode = 41
        versionName = "4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "STRAVA_CLIENT_ID", "\"${providers.gradleProperty("STRAVA_CLIENT_ID").getOrElse("")}\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"${providers.gradleProperty("STRAVA_CLIENT_SECRET").getOrElse("")}\"")
    }

    signingConfigs {
        // Only wire the upload keystore when its properties are actually available
        // (normally ~/.gradle/gradle.properties). Reading them unconditionally resolved
        // them at configuration time, which broke every task -- debug builds and unit
        // tests included -- on any checkout without the keystore. Release builds still
        // refuse to produce an unsigned artifact; see the guard below.
        if (hasUploadKeystore) {
            create("release") {
                storeFile = file(providers.gradleProperty("STOCHASTIC_UPLOAD_STORE_FILE").get())
                storePassword =
                    providers.gradleProperty("STOCHASTIC_UPLOAD_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("STOCHASTIC_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("STOCHASTIC_UPLOAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (hasUploadKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
            }
        }
        create("releaseLocal") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Room writes schemas in :shared; MigrationTestHelper reads them from this module's test assets.
        getByName("androidTest").assets.directories.add("$rootDir/shared/schemas")
    }
}

dependencies {
    constraints {
        // play-services-base/basement drag in fragment 1.1.0, which Play Console
        // flags as outdated. The app itself is Compose-only and uses no fragments.
        implementation(libs.androidx.fragment)
    }

    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.play.services.location)
    implementation(libs.vico.compose.m3)
    implementation(libs.reorderable)
    implementation(libs.okhttp)
    implementation(libs.tink.android)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Without the keystore the release build types would silently produce unsigned artifacts.
// Fail them loudly instead; debug and test tasks stay unaffected.
if (!hasUploadKeystore) {
    tasks.configureEach {
        if (name.matches(Regex("(assemble|package|bundle)Release.*"))) {
            doFirst {
                error(
                    "Release builds require the STOCHASTIC_UPLOAD_* Gradle properties " +
                        "(STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). " +
                        "Set them in ~/.gradle/gradle.properties or pass them with -P.",
                )
            }
        }
    }
}
