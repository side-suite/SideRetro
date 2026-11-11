plugins { id("com.android.library") }
android {
    namespace = "com.swordfish.libretrodroid"
    compileSdk = 36
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 21
        ndk { abiFilters += "arm64-v8a" }
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
    }
    externalNativeBuild { cmake {
        version = "3.22.1"
        path = file("src/main/cpp/CMakeLists.txt")
    } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") }
