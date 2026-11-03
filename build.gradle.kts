buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 ships its own Kotlin; keep it in step with the Compose compiler plugin (libs.versions.toml).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
