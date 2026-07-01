import net.fabricmc.loom.util.ModPlatform

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.architectury.loom)
}

val loader: ModPlatform = loom.platform.get()
val loaderName: String = property("loom.platform").toString()
val mcVersion: String = property("vers.mcVersion").toString()
val modId: String = property("mod.id").toString()
val javaVersion: Int = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17

group = property("mod.group").toString()
version = "${property("mod.version")}+$mcVersion-$loaderName"
base.archivesName = "${property("mod.id")}-$loaderName"

loom {
    silentMojangMappingsLicense()
    runConfigs.all {
        // Only generate IDE run configs for the active Stonecutter version.
        ideConfigGenerated(stonecutter.current.isActive)
        runDir("../../run")
    }
    // Datagen run (`runData`): drives com.tonywww.blackboard.data.BlackboardDataGen.
    // Forge/NeoForge only fire GatherDataEvent for mods named via --mod, so pass ours explicitly.
    // NB: `modId` is hoisted to a top-level val — inside this block `property(...)` would resolve
    // to RunConfigSettings, not Project.
    runConfigs.create("data") {
        data()
        programArgs("--all", "--mod", modId)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.minecraftforge.net/")
    maven("https://repo.nyon.dev/releases") // KotlinLangForge
    maven("https://maven.latvian.dev/releases") // KubeJS (soft dependency)
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (loader == ModPlatform.FORGE) {
        "forge"("net.minecraftforge:forge:$mcVersion-${property("vers.deps.fml")}")
    } else {
        "neoForge"("net.neoforged:neoforge:${property("vers.deps.fml")}")
    }

    // Kotlin language adapter / runtime (modLoader = "klf").
    modImplementation("dev.nyon:KotlinLangForge:${property("deps.klf")}") {
        if (loader == ModPlatform.FORGE) {
            // Forge 1.20.1 ONLY: Loom remaps KLF's transitive kotlin-stdlib into a jar that keeps
            // the `Multi-Release: true` manifest flag but drops META-INF/versions/, which crashes
            // securejarhandler 2.1.10's ClasspathLocator (UnionFileSystem NoSuchFileException:
            // /META-INF/versions) during dev mod discovery. Forge's language provider can see the
            // Kotlin Gradle plugin's stdlib, so dropping the remapped copy is safe here.
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        }
    }

    // NeoForge 1.21.1 dev ONLY: KLF's language provider (`KotlinLanguageLoader`) loads in FML's
    // isolated service/plugin module layer ("LAYER PLUGIN"), which does NOT see the remapped Kotlin
    // mods on the game classpath. In production NeoForge extracts KLF's JiJ'd kotlin-stdlib into that
    // layer, but Architectury Loom's userdev locator does not process KLF's Jar-in-Jar, so the
    // provider can't resolve `kotlin.Pair` and FML reports `Missing language klf` (see moddevgradle,
    // which the KLF author's own mods use, does this automatically).
    //
    // `forgeRuntimeLibrary` puts these on the dev boot/runtime classpath (as automatic modules
    // readable by KLF's automatic module) so the provider can instantiate. KotlinLanguageLoader and
    // the other dev/nyon/klf classes reference only kotlin-stdlib + kotlin.reflect.KClass.
    // Dev-only: production is unaffected (KLF ships these via Jar-in-Jar).
    if (loader == ModPlatform.NEOFORGE) {
        "forgeRuntimeLibrary"("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
        "forgeRuntimeLibrary"("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    }

    // KubeJS — soft dependency (compile-only; not bundled, not required at runtime).
    // compat/kubejs/* only loads when KubeJS is present (via kubejs.plugins.txt); the entrypoint
    // (P7-B) guards registration with a mod-loaded check.
    //
    // KubeJS pulls `com.github.rtyley:animated-gif-lib-for-java` (used internally for image/gif
    // handling) as a transitive — but that artifact lives only on JitPack, which is not in our
    // repository list (and is unreliable from some networks). Our compat code never touches it, so
    // exclude it to keep the compile-only classpath resolvable without JitPack.
    fun ExternalModuleDependency.excludeAnimatedGifLib() =
        exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
    if (loader == ModPlatform.FORGE) {
        modCompileOnly("dev.latvian.mods:kubejs-forge:${property("deps.kubejs")}") { excludeAnimatedGifLib() }
        modCompileOnly("dev.latvian.mods:rhino-forge:${property("deps.rhino")}")
    } else {
        modCompileOnly("dev.latvian.mods:kubejs-neoforge:${property("deps.kubejs")}") { excludeAnimatedGifLib() }
        modCompileOnly("dev.latvian.mods:rhino:${property("deps.rhino")}")
    }

    // 单元测试（纯 Kotlin 逻辑）。
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "id" to project.property("mod.id"),
        "name" to project.property("mod.name"),
        "version" to project.property("mod.version"),
        "pack_format" to project.property("pack.format"),
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")) {
        expand(props)
    }
    // Keep only the metadata file relevant to the current loader.
    exclude(if (loader == ModPlatform.NEOFORGE) "META-INF/mods.toml" else "META-INF/neoforge.mods.toml")
}
