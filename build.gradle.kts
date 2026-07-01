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
    maven("https://maven.architectury.dev/") // Architectury (KubeJS runtime transitive, dev-only)
    maven("https://maven.sighs.cc/repository/maven-public/") // ApricityUI (soft dependency)
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
        // Dev-only: load KubeJS + its Architectury/Rhino transitives into runClient so the mod's
        // KubeJS-compat integration (compat/kubejs) is exercised in dev. NOT published — a soft
        // dependency (players who want it install KubeJS themselves). MixinExtras is already bundled by
        // Forge (exclude to avoid a duplicate service module); animated-gif-lib lives only on JitPack
        // (excluded — KubeJS only needs it for gif assets we never load).
        modLocalRuntime("dev.latvian.mods:kubejs-forge:${property("deps.kubejs")}") {
            excludeAnimatedGifLib()
            exclude(group = "io.github.llamalad7")
        }
    } else {
        modCompileOnly("dev.latvian.mods:kubejs-neoforge:${property("deps.kubejs")}") { excludeAnimatedGifLib() }
        modCompileOnly("dev.latvian.mods:rhino:${property("deps.rhino")}")
    }

    // ApricityUI (晴雪UI) — client-side world rendering, SOFT dependency (compile-only; players install
    // it separately, and the renderer falls back to no-op without it — see api/render + client/).
    // Artifact id is loader+MC qualified; versions differ (Forge is AUI's primary target, NeoForge lags).
    // `isTransitive = false`: AUI's POM declares optional soft-integration deps (JitPack animated-gif-lib,
    // Modrinth sodium/iris/jei/lan-server-properties) that live on repos we don't have; we only compile
    // against AUI's own API (ApricityUI/WorldWindow/Document/Element), so drop the transitives.
    // Dev runClient does NOT bundle AUI, so the renderer stays no-op in dev unless AUI is added to the
    // runtime classpath (e.g. modLocalRuntime). See docs/references/apricity-ui.md.
    if (loader == ModPlatform.FORGE) {
        modCompileOnly("com.sighs:ApricityUI-forge-1.20.1:${property("deps.aui")}") { isTransitive = false }
        // Dev-only: also load AUI in the runClient so the world renderer actually activates. modLocalRuntime
        // is NOT published. isTransitive=false drops AUI's optional soft-integration deps (Modrinth
        // sodium/iris/jei, KubeJS/Rhino) — the JLaTeXMath <img> renderer needs none of them.
        modLocalRuntime("com.sighs:ApricityUI-forge-1.20.1:${property("deps.aui")}") { isTransitive = false }
    } else {
        modCompileOnly("com.sighs:ApricityUI-neoforge-1.21.1:${property("deps.aui")}") { isTransitive = false }
        // Dev-only: load AUI in the NeoForge runClient too, so the renderer activates on 1.21.1.
        // AUI-neoforge 1.1.2's neoforge.mods.toml only hard-requires neoforge+minecraft (its
        // kubejs/rhino/Modrinth deps are optional soft integrations), so isTransitive=false is safe and
        // the <img> renderer works standalone. JLaTeXMath is already on the run classpath via
        // forgeRuntimeLibrary (unconditional, below).
        modLocalRuntime("com.sighs:ApricityUI-neoforge-1.21.1:${property("deps.aui")}") { isTransitive = false }
    }

    // JLaTeXMath — pure-Java LaTeX renderer (org.scilab.forge). The renderer rasterizes each question
    // to a white-on-transparent PNG on the JVM side and hands it to AUI as an <img>, so math renders
    // without any page JS or KubeJS.
    //   - `implementation`        → compile classpath (mod code references TeXFormula/TeXIcon).
    //   - `forgeRuntimeLibrary`   → dev runClient classpath. Forge/NeoForge ModLauncher does NOT load
    //                               plain `implementation` libraries into the mod's transforming
    //                               classloader, so without this every TeXFormula(...) throws
    //                               NoClassDefFoundError at runtime (same reason KLF's stdlib needs it
    //                               on NeoForge above). Puts the jar on the dev boot/runtime classpath.
    //   - `include`               → production Jar-in-Jar so shipped players don't install it.
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    "forgeRuntimeLibrary"("org.scilab.forge:jlatexmath:1.0.7")
    include("org.scilab.forge:jlatexmath:1.0.7")

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
