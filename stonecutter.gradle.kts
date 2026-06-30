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
