# 外部参考：多加载器构建（Architectury Loom + KotlinLangForge）

> 来源：**KotlinLangForge 自身的 testmod**（`testmod/build.gradle.kts`、节点 `gradle.properties`、`gradle/libs.versions.toml`）——「KLF + Forge + NeoForge + Stonecutter + Kotlin」唯一已验证的真实组合；以及 KubeJS 官方 README（Gradle 接入）。均已核实。
> Stonecutter 见 [stonecutter.md](stonecutter.md)；KLF 运行时见 [kotlinlangforge.md](kotlinlangforge.md)；KubeJS 见 [kubejs-integration.md](kubejs-integration.md)。

> ⚠️ **重要更正（2026-06-30）**：早期版本采用 **ModDevGradle**（来自纯 Java 模板）。经核实 **KLF 官方用 Architectury Loom**（其 testmod 即 `dev.architectury.loom` + `modImplementation`/`forge`/`neoForge`），ModDevGradle 无 `modImplementation` 且 KLF 在其下接入未被验证。故本项目改用 **Architectury Loom（flat）+ Stonecutter**，与 KLF testmod 完全一致。

目录：
1. 构建工具结论（已验证）
2. 目录与属性布局
3. settings.gradle.kts
4. stonecutter.gradle.kts
5. 共享 build.gradle.kts（flat，单脚本双加载器）
6. 元数据文件（mods.toml / neoforge.mods.toml）
7. KubeJS 软依赖接入（Loom）
8. 待验证项
9. dev 运行（runClient）：已验证 + 崩溃根因与修复

---

## 1. 构建工具结论（已验证）

| 目标 | `loom.platform` | 加载器依赖（Loom 配置） | KLF 坐标 | Java |
| --- | --- | --- | --- | --- |
| NeoForge 1.21.1 | `neoforge` | `net.neoforged:neoforge:21.1.x`（`neoForge` 配置）| `dev.nyon:KotlinLangForge:2.12.1-k2.4.0-3.0+neoforge` | 21 |
| Forge 1.20.1 | `forge` | `net.minecraftforge:forge:1.20.1-47.4.4`（`forge` 配置）| `dev.nyon:KotlinLangForge:2.12.1-k2.4.0-2.0+forge` | 17 |

- 构建：**Architectury Loom**（`dev.architectury.loom`，KLF 当前 `1.11-SNAPSHOT`）。**flat 模式**：单一 `build.gradle.kts`，按 `loom.platform`（节点属性）切 Forge/NeoForge。
- Kotlin `2.4.0`（与 KLF 内置一致）；Stonecutter `0.9.6`，`kotlinController = true`；映射 `loom.officialMojangMappings()`。
- **KLF 依赖配置名 = `modImplementation(...)`**（Loom 提供；与 KLF README 一致）。

> `forge`/`neoForge` 是 Architectury Loom 的依赖配置；`loom.platform.get()` 返回 `ModPlatform`。这是 Stonecutter 文档的「Flat → Architectury」方案；KLF testmod 已用它跑通 Forge 1.20.1（forge 47.4.4）与多个 NeoForge 版本。

---

## 2. 目录与属性布局

```
Blackboard/
├─ settings.gradle.kts
├─ stonecutter.gradle.kts          # 控制器（active + parameters）
├─ build.gradle.kts               # 单一共享脚本（flat，双加载器）
├─ gradle.properties               # Gradle 选项 + mod.* 元数据
├─ gradle/libs.versions.toml       # 版本目录（kotlin / architectury-loom）
├─ versions/
│  ├─ 1.20.1-forge/gradle.properties
│  └─ 1.21.1-neoforge/gradle.properties
└─ src/main/
   ├─ kotlin/com/tonywww/blackboard/...
   └─ resources/
      ├─ META-INF/mods.toml              # Forge
      └─ META-INF/neoforge.mods.toml     # NeoForge
```

`gradle/libs.versions.toml`（据 KLF，已核实）：
```toml
[versions]
kotlin = "2.4.0"
architectury-loom = "1.11-SNAPSHOT"

[plugins]
kotlin = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
architectury-loom = { id = "dev.architectury.loom", version.ref = "architectury-loom" }
```

`./gradle.properties`（根）：
```properties
org.gradle.jvmargs=-Xmx2G

mod.id=blackboard
mod.name=Blackboard
mod.version=0.1.0
mod.group=com.tonywww.blackboard
```

`./versions/1.20.1-forge/gradle.properties`（`vers.deps.fml=47.4.4` 来自 KLF testmod 的 1.20.1 节点，已核实）：
```properties
vers.mcVersion=1.20.1
vers.deps.fml=47.4.4
loom.platform=forge
deps.klf=2.12.1-k2.4.0-2.0+forge
```

`./versions/1.21.1-neoforge/gradle.properties`：
```properties
vers.mcVersion=1.21.1
vers.deps.fml=21.1.193      # 用实际最新的 1.21.1 NeoForge 版本
loom.platform=neoforge
deps.klf=2.12.1-k2.4.0-3.0+neoforge
```

> `loom.platform=forge`/`neoforge` 写在**节点 gradle.properties**，是 flat Loom 切换加载器的关键（已核实）。`deps.klf` 已选定稳定版 **2.12.1**（两变体 POM 均已发布，见 kotlinlangforge.md §4）；`vers.deps.fml`（NeoForge 21.1.x）用构建时最新。

---

## 3. settings.gradle.kts

```kotlin
rootProject.name = "Blackboard"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins { id("dev.kikugie.stonecutter") version "0.9.6" }

stonecutter {
    kotlinController = true
    create(rootProject) {
        version("1.20.1-forge", "1.20.1")
        version("1.21.1-neoforge", "1.21.1")
        vcsVersion = "1.21.1-neoforge"
    }
}
```

> flat Loom 用**单一共享 `build.gradle.kts`**，故节点**不**指定 `.buildscript(...)`（与 ModDevGradle 的 split buildscript 不同）。

---

## 4. stonecutter.gradle.kts

```kotlin
plugins { id("dev.kikugie.stonecutter") }

stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')   // forge / neoforge
    constants { match(loader, "forge", "neoforge") }        // 启用 //? if forge / //? if neoforge
}
```

---

## 5. 共享 build.gradle.kts（flat，单脚本双加载器）

> 基于 KLF testmod 的 `build.gradle.kts`（已核实），适配 Blackboard。

```kotlin
import net.fabricmc.loom.util.ModPlatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.architectury.loom)
}

val loader = loom.platform.get()
val mcVersion = property("vers.mcVersion").toString()

group = property("mod.group").toString()
version = "${property("mod.version")}+$mcVersion"
base.archivesName = "${property("mod.id")}-${loader.id()}"

loom {
    silentMojangMappingsLicense()
    if (stonecutter.current.isActive) {
        runConfigs.all { ideConfigGenerated(true); runDir("../../run") }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.minecraftforge.net/")
    maven("https://repo.nyon.dev/releases")        // KotlinLangForge
    maven("https://maven.latvian.dev/releases")     // KubeJS（软依赖，见 §7）
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (loader == ModPlatform.FORGE)
        "forge"("net.minecraftforge:forge:$mcVersion-${property("vers.deps.fml")}")
    else
        "neoForge"("net.neoforged:neoforge:${property("vers.deps.fml")}")

    // 语言适配器（运行时必需）。必须排除 Loom 重映射后的 kotlin-stdlib——它保留了
    // Multi-Release 清单标志却丢了 META-INF/versions/，会让 Forge 1.20.1 的 securejarhandler
    // 在 dev 运行（runClient）时崩溃（详见 §9.1）。Kotlin 插件已提供正版 stdlib。
    modImplementation("dev.nyon:KotlinLangForge:${property("deps.klf")}") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // KubeJS 软依赖：仅编译期，运行期由玩家安装（见 §7）。需排除只在 JitPack 上的 gif 库传递依赖。
    // modCompileOnly("dev.latvian.mods:kubejs-${if (loader == ModPlatform.NEOFORGE) "neoforge" else "forge"}:${property("deps.kubejs")}") {
    //     exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
    // }
}

val javaVersion = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17

tasks {
    processResources {
        val props = mapOf(
            "id" to property("mod.id"),
            "name" to property("mod.name"),
            "version" to property("mod.version"),
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml")) { expand(props) }
        // 仅保留当前加载器的元数据文件
        exclude(if (loader == ModPlatform.NEOFORGE) "META-INF/mods.toml" else "META-INF/neoforge.mods.toml")
    }
    withType<JavaCompile> { options.release = javaVersion }
    withType<KotlinCompile> { compilerOptions { jvmTarget = JvmTarget.fromTarget(javaVersion.toString()) } }
}

java { withSourcesJar() }
```

> KLF 已提供 Kotlin 运行时库（见 [kotlinlangforge.md](kotlinlangforge.md)「内置库」），产物**勿重复打包**。但 Loom 会把 KLF 传递来的 `kotlin-stdlib` 重映射成一份「声明 `Multi-Release: true`、却无 `META-INF/versions/`」的 jar，会让 **Forge 1.20.1 的 dev 运行崩溃**，故上面 `exclude` 掉它（Kotlin 插件已提供正版）。详见 §9.1。

---

## 6. 元数据文件

**Forge** `src/main/resources/META-INF/mods.toml`：
```toml
modLoader = "klf"
loaderVersion = "[1,)"
license = "All Rights Reserved"

[[mods]]
modId = "${id}"
version = "${version}"
displayName = "${name}"

[[dependencies.${id}]]
modId = "forge"
mandatory = true
versionRange = "[47,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${id}]]
modId = "minecraft"
mandatory = true
versionRange = "[1.20.1,1.21)"
ordering = "NONE"
side = "BOTH"
```

**NeoForge** `src/main/resources/META-INF/neoforge.mods.toml`：
```toml
modLoader = "klf"
loaderVersion = "[1,)"
license = "All Rights Reserved"

[[mods]]
modId = "${id}"
version = "${version}"
displayName = "${name}"

[[dependencies.${id}]]
modId = "neoforge"
type = "required"
versionRange = "[21.1,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.${id}]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.1,1.21.2)"
ordering = "NONE"
side = "BOTH"
```

> `${...}` 由 `processResources.expand(props)` 填充（Groovy `SimpleTemplateEngine` 语法）。Forge 用 `mandatory=true`，NeoForge 用 `type="required"`。版本范围数值按实际依赖确认。

> ⚠️ **不要把 `klf` 写成 `[[dependencies]]`**：KLF 只注册为 Forge/NeoForge 的 **LANGPROVIDER**（不是 MOD），`modLoader = "klf"` + `loaderVersion` 已足够约束。若误加 `modId = "klf"` 的 mandatory 依赖，dev 运行时会报 `Missing mandatory dependencies: Mod ID 'klf' ... [MISSING]` 而无法进游戏（详见 §9.2）。上面只对 `forge`/`neoforge` 与 `minecraft` 声明依赖即可。

---

## 7. KubeJS 软依赖接入（Loom）

- 仓库（KubeJS README 确认）：`maven("https://maven.latvian.dev/releases")`（group `dev.latvian.mods` / `dev.latvian.apps`）。
- 工件：`kubejs-forge`（1.20.1）/ `kubejs-neoforge`（1.21.1）。
- 软依赖写法（Loom）：`modCompileOnly("dev.latvian.mods:kubejs-<loader>:<ver>")`；本地测试可加 `modLocalRuntime(...)`。
- ⚠️ **必须排除 JitPack 传递依赖**：`kubejs-neoforge` 传递依赖 `com.github.rtyley:animated-gif-lib-for-java`（仅发布在 JitPack，本项目仓库列表未含、国内也不稳）。因 KubeJS 是 `modCompileOnly`（不打包、运行期不需要）且本模组不碰该 gif 库，故 `exclude` 之，免引 JitPack：
  ```kotlin
  modCompileOnly("dev.latvian.mods:kubejs-neoforge:${property("deps.kubejs")}") {
      exclude(group = "com.github.rtyley", module = "animated-gif-lib-for-java")
  }
  ```
  否则解析失败会拖垮**整个配置阶段**——配置按需关闭时，连 `:1.20.1-forge:build` 也会一并失败。
- 版本：取与目标 MC 对应的最新 `kubejs-<loader>`（1.20.1 → KubeJS 6；1.21.1 → KubeJS 7）。详见 [kubejs-integration.md](kubejs-integration.md)。

---

## 8. 待验证项（落地时确认并回写）

1. **具体版本号**：`deps.klf`、NeoForge `vers.deps.fml`（21.1.x）、`deps.kubejs`——用实际最新。
2. ~~**Architectury Loom** 对 Forge 1.20.1 的稳定性~~ ✅ **已验证**：`:1.20.1-forge:runClient` 实测进入单人世界（Loom `1.11.458`、forge 47.4.4、JDK 17.0.16），`[blackboard/]: Blackboard loaded`。期间修掉两个 dev 运行崩溃，见 §9。
3. `base.archivesName` 里的 `loader.id()`：确认 `ModPlatform` 取字符串的正确方法（或改用 `property("loom.platform")`）。
4. `expand` 对 toml 字面 `$` 的转义，落地时验证 `processResources` 输出正确。

---

## 9. dev 运行（runClient）：已验证 + 崩溃根因与修复

> 2026-07-01 实测：`:1.20.1-forge:runClient` 成功进入单人世界（JDK 17.0.16、Loom 1.11.458、forge 47.4.4），日志 `[blackboard/]: Blackboard loaded`、玩家在 (1.5, -60.0, 5.5) 出生。两个 loader 的 `build` 亦绿、26 单测全过。下面记录此前 `runClient` 崩溃的两个根因与修复。

### 9.1 securejarhandler 崩溃：重映射的 kotlin-stdlib 丢了 META-INF/versions/
- **现象**：`build` 通过但 `runClient` 崩溃——`java.io.UncheckedIOException: cpw.mods.niofs.union.UnionFileSystem$NoSuchFileException: /META-INF/versions`，栈顶 `Jar.<init>(Jar.java:138)`（securejarhandler 2.1.10 的 `ClasspathLocator`）。`build` 不触发该目录联合扫描，故只在 dev 运行暴露。
- **根因**：元凶 jar = **Loom 重映射后的 kotlin-stdlib**（`kotlin-stdlib-<hash>-2.4.0.jar`，KLF 的传递依赖）。重映射**保留了清单里的 `Multi-Release: true`、却删掉了 `META-INF/versions/`**；securejarhandler 对「声明 MR」的 jar 会 `Files.walk(.../META-INF/versions)`，目录不存在即抛 `NoSuchFileException`。
- **修复**：在 KLF 的 `modImplementation` 上 `exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")`（见 §5）。Kotlin Gradle 插件已提供带 `META-INF/versions/` 的正版 stdlib；KLF 不打进产物 jar，排除只影响 dev classpath。

### 9.2 klf 被误声明为 mandatory MOD 依赖
- **现象**（被 9.1 掩盖，修完才暴露）：`Missing or unsupported mandatory dependencies: Mod ID 'klf', Requested by 'blackboard', Expected '[1,)', Actual '[MISSING]'`，无法进游戏。
- **根因**：**KLF 只注册为 LANGPROVIDER**（日志 `FML Language Providers: klf@2.12.1`），从不作为 MOD 出现。而 `mods.toml` 误加了 `modId = "klf"` 的 mandatory `[[dependencies]]` 块。
- **修复**：删除两个元数据文件里对 `klf` 的 `[[dependencies]]` 块（见 §6 警告）。`modLoader = "klf"` + `loaderVersion = "[1,)"` 已强制要求 klf 语言提供者。

### 9.3 已否证的假说
- 「JDK 17.0.13+ 回归」**已否证**：在 JDK 17.0.2 与 17.0.16 上崩溃完全一致，与 JDK 无关。曾在 `gradle.properties` 加机器相关 JDK pinning 测试，已回退删除（不可移植）。回退后 `runClient` 自动选用 17.0.16 仍正常进游戏，证明修复与 JDK 无关。

### 9.4 关键命令与排错
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
.\gradlew.bat :1.20.1-forge:runClient --console=plain   # 打开 GUI，关闭窗口即结束
```
- runConfig 的 `runDir` 指向项目根 `run/`。
- 若上次运行被中断导致 `fabric-loom` 缓存锁残留（`Lock for cache ... is currently held by pid ...`），先 `.\gradlew.bat --stop` 再重跑（Loom 会自动清理 `ACQUIRED_PREVIOUS_OWNER_MISSING` 锁并重建缓存）。

---

## 修订记录
- 2026-06-30：初版按 ModDevGradle（来自官方/rotgruengelb 模板，纯 Java）。
- 2026-06-30：**更正为 Architectury Loom（flat）+ Stonecutter**——依据 KLF testmod 真实组合（loom `1.11-SNAPSHOT`、`loom.platform` 节点属性、forge `47.4.4`/1.20.1、`modImplementation` 接入 KLF），补 KubeJS 经 `maven.latvian.dev` 的 Loom 软依赖接入。
- 2026-07-01：补 §9——`runClient` 实测进游戏；记两个崩溃根因与修复（重映射 kotlin-stdlib 缺 `META-INF/versions/` → 排除；klf 被误声明为 mandatory MOD 依赖 → 删除）；§5/§7 补 KLF stdlib 与 KubeJS animated-gif-lib 的 `exclude`；§6 补「klf 不是依赖」警告；§8-2 标记已验证。
