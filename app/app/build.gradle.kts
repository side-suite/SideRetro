import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing is read from `keystore.properties` (gitignored) when a complete,
// usable configuration is present. Until then, release builds deliberately use the
// standard debug key so they can be installed for on-device qualification; such APKs
// must never be published. See /RELEASE.md before cutting a public release.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) FileInputStream(keystorePropsFile).use(::load)
}
val requiredSigningProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
// `keystore.properties` lives at the Gradle root, so relative keystore paths must be
// resolved from that same directory. `Project.file` here would resolve from `app/app/`
// (this module) and silently fall back to the debug signer even when the key exists.
val releaseStoreFile = keystoreProps.getProperty("storeFile")
    ?.takeIf(String::isNotBlank)
    ?.let(rootProject::file)
val hasReleaseKeystore = requiredSigningProperties.all { key ->
    !keystoreProps.getProperty(key).isNullOrBlank()
} && releaseStoreFile?.isFile == true

android {
    namespace = "fi.palonkorpi.sideretro"
    compileSdk = 36

    defaultConfig {
        applicationId = "fi.palonkorpi.sideretro"
        minSdk = 31          // Sidephone SP-01 = Android 12 / API 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // The SP-01 is arm64-v8a. Single ABI keeps the APK to the three cores we actually ship.
        ndk { abiFilters += "arm64-v8a" }
    }

    packaging {
        jniLibs {
            // Extract the cores to nativeLibraryDir on install so LibretroDroid can dlopen them
            // by absolute path. With the modern default (extractNativeLibs=false) they stay
            // compressed inside the APK and only System.loadLibrary can reach them.
            useLegacyPackaging = true
            keepDebugSymbols += "*/*/*_libretro_android.so"
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // No shrinking: R8 would have to be taught about LibretroDroid's JNI entry points and
            // its reflective @OnLifecycleEvent observer, and the APK is dominated by the cores
            // anyway — three .so files are ~6 MB against a few hundred KB of our own code.
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.Swordfish90:LibretroDroid:0.14.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    // Pinned to 2.6.x on purpose: GLRetroView drives itself through the reflective
    // @OnLifecycleEvent observer, which later lifecycle versions stop honouring.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-common:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
