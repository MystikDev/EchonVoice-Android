// AGP supplies Android Kotlin integration; pin its compiler to the same version
// as Compose and serialization instead of applying the legacy Kotlin Android plugin.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

// This repo lives under iCloud-synced ~/Documents, which creates "<file> 2.ext"
// conflicted copies of churny build artifacts and intermittently breaks resource
// parsing. Redirect build output outside the synced tree — but ONLY locally;
// CI (e.g. GitHub runners) uses the standard app/build layout.
if (rootDir.path.contains("/Documents/")) {
    val externalBuildRoot = File(System.getProperty("user.home"), ".cache/echon-android-build")
    rootProject.layout.buildDirectory.set(File(externalBuildRoot, "root"))
    subprojects {
        layout.buildDirectory.set(File(externalBuildRoot, project.name))
    }
}
