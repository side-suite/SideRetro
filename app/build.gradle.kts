// Toolchain matches the sibling SideHome and SideRetro-spike builds:
// AGP 9.2.1 · Gradle 9.4.1 · JDK 21.
plugins {
    // AGP 9 ships built-in Kotlin support, so the standalone kotlin-android plugin is not applied.
    id("com.android.application") version "9.2.1" apply false
}
