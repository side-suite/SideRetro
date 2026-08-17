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
        // LibretroDroid is published on JitPack only. Note the capitalised artifactId —
        // the upstream README's lowercase `libretrodroid` coordinate does not resolve.
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "SideRetro"
include(":app")
