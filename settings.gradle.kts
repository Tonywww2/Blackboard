rootProject.name = "Blackboard"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    // Lets Gradle auto-provision the required JDKs (e.g. 21 for the NeoForge node).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    kotlinController = true
    create(rootProject) {
        // versions/<project>/  with a separate logical version for comment processing
        version("1.20.1-forge", "1.20.1")
        version("1.21.1-neoforge", "1.21.1")
        // Active/VCS version: Forge 1.20.1 (Java 17). Switch with the Stonecutter task/IDE plugin.
        vcsVersion = "1.20.1-forge"
    }
}
