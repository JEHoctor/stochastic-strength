import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "io.github.fowles.stochastic_strength.shared"
        compileSdk { version = release(37) { minorApiLevel = 1 } }
        minSdk = 33
        withHostTestBuilder {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()
    // No iosX64: GitHub's macOS runners are Apple Silicon.
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "Shared"; isStatic = true }
    }
    // Migrations keep upstream's `db` parameter name over Room's `connection`;
    // silence the per-override named-argument warning that choice produces.
    compilerOptions {
        freeCompilerArgs.add("-Xwarning-level=PARAMETER_NAME_CHANGED_ON_OVERRIDE:disabled")
        // Room KMP needs the `expect object AppDatabaseConstructor`; the Beta warning is noise here.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // api: AppDatabase's public surface is Room's (RoomDatabase supertype, Builder in configure()),
            // and app calls DAO methods on it, so app needs Room on its compile classpath too.
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // Not used directly. Room 2.8.4's MigrationTestHelper needs kotlinx-serialization >= 1.8.1, but
            // lifecycle 2.11 pulls 1.7.3 into the app runtime and Gradle's consistent resolution then pins
            // the androidTest classpath to that. Declaring it here lifts the app runtime to what Room needs.
            implementation(libs.kotlinx.serialization.core)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.json)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
