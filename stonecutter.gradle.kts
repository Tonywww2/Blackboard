plugins {
    id("dev.kikugie.stonecutter")
}

// The version whose state is reflected in the shared src/ directory.
// Forge 1.20.1 targets Java 17 (matches the current JDK); switch via the Stonecutter IDE plugin or task.
stonecutter active "1.20.1-forge"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')
    // Enables `//? if forge { ... }` / `//? if neoforge { ... }` in source comments.
    constants {
        match(loader, "forge", "neoforge")
    }
}

// ---------------------------------------------------------------------------------------------------
// One-click multi-loader publish. Runs each node's `publishMods` (CurseForge upload — see
// build.gradle.kts) for EVERY version, regardless of the currently-active Stonecutter version.
//
//   ./gradlew publishAllVersions
// ---------------------------------------------------------------------------------------------------
tasks.register("publishAllVersions") {
    group = "publishing"
    description = "Builds and publishes every Minecraft/loader version to CurseForge."
    dependsOn(stonecutter.tasks.named("publishMods").map { it.values })
}

// Upload the per-loader files one at a time instead of in parallel, to avoid CurseForge API throttling.
stonecutter.tasks.order("publishCurseforge")
