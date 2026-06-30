# 外部参考：Stonecutter 多版本/多加载器

> 来源：官方文档 https://stonecutter.kikugie.dev/wiki/ 与官方/社区模板（已核实，非臆测）。
> 适用：Blackboard 同时构建 **Forge 1.20.1** 与 **NeoForge 1.21.1**。
> 具体构建脚本（ModDevGradle / KLF）见 [multiloader-build.md](multiloader-build.md)；本文件聚焦 Stonecutter 本身。

目录：
1. 要求与插件
2. 项目模型（Tree / Branch / Node）
3. settings.gradle.kts
4. stonecutter.gradle.kts（控制器）
5. build.gradle.kts（每节点模板）
6. 版本化注释语法
7. 常量 / swap / 替换
8. Blackboard 具体配置
9. 关键陷阱

---

## 1. 要求与插件

- **Gradle ≥ 9.0**（模板用 9.6）。
- Stonecutter 插件：`dev.kikugie.stonecutter`，当前 `0.9.6`。
- 插件仓库（`settings.gradle.kts` 的 `pluginManagement.repositories`）：
  - `gradlePluginPortal()`、`mavenCentral()`
  - `maven("https://maven.kikugie.dev/releases")`、`maven("https://maven.kikugie.dev/snapshots")`
  - 加载器仓库：`maven("https://maven.neoforged.net/releases/")`、`maven("https://maven.fabricmc.net/")`

---

## 2. 项目模型（三层）

- **Tree（树）**：根项目，持有 `stonecutter.gradle.kts`，是所有子项目的同步点。`stonecutter.create(rootProject) {}`。
- **Branch（分支）**：持有共享 `src/` 与 `versions/...`。根分支用空串 `""` 表示，通常“不可见”（与树同目录）。多模块/多加载器时可显式 `branch("api")`。
- **Node（节点）**：每个受支持的版本变体，对应 `versions/<project>/`，产出最终 jar。节点三要素：
  - **project**：目录名，节点在分支内的唯一标识（如 `1.20.1-forge`）。
  - **version**：用于注释预处理的逻辑版本（如 `1.20.1`）。多加载器时必须与 project 分离（见下）。
  - **build script**：默认 `build.gradle(.kts)`，可按节点指定不同脚本。

> **本项目采用「Flat + Split buildscript」**（官方文档推荐的多加载器方式）：单一 `src/`，每个加载器一个 `build.<loader>.gradle.kts`，用各自原生工具链（ModDevGradle）。

---

## 3. settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases")  { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

stonecutter {
    create(rootProject) {
        // 为每个加载器创建 `versions/{project}-{loader}` 节点，并指定 `build.{loader}.gradle.kts`
        fun match(project: String, vararg loaders: String, version: String = project) {
            for (loader in loaders)
                version("$project-$loader", version).buildscript("build.$loader.gradle.kts")
        }
        match("1.20.1", "forge")      // -> versions/1.20.1-forge, version=1.20.1, build.forge.gradle.kts
        match("1.21.1", "neoforge")   // -> versions/1.21.1-neoforge, version=1.21.1, build.neoforge.gradle.kts
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "Blackboard"
```

要点（已核实）：
- `version("项目名", "逻辑版本")`：**必须分离**，否则 `1.20.1-forge` 会被当成 SemVer 预发布（`1.20 < 1.20.1-forge < 1.20.1`），破坏 `//? if >=1.20.1` 判断。
- `.buildscript("build.forge.gradle.kts")`：为该节点指定专用脚本（split buildscript）。脚本名**不能**叫 `stonecutter.gradle.kts`。
- `vcsVersion`：提交前用 `Reset active version` 任务回到的版本（默认首个）。
- 也支持数据驱动（`stonecutter.properties.toml` 等）：`"1.20.1-forge:1.20.1:build.forge.gradle.kts"`。本项目用上面的 Kotlin DSL 即可。

---

## 4. stonecutter.gradle.kts（控制器，单例运行）

```kotlin
plugins { id("dev.kikugie.stonecutter") }

stonecutter active "1.21.1-neoforge"   // 当前编辑/链接的活动版本，必须恰好赋值一次

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)  // 如 ("1.20.1","forge")

    // 加载器常量：使注释 `//? if forge {` / `//? if neoforge {` 可用
    constants { match(loader, "forge", "neoforge") }

    // 也可定义 swap / 依赖谓词 / 替换（见 §7）
    // swaps["minecraft"] = "\"${current.version}\";"
    // replacements.string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }
}
```

要点：
- `parameters {}`（懒求值）是配置**文件处理器参数**（constants/swaps/dependencies/replacements）的推荐位置；**不要**在其中配置项目依赖或任务。
- `active` 三种形式：字面量、`file("active.txt")`、`null`（detached，CI 构建全部版本）。
- 任务聚合/排序：`stonecutter.tasks.named("publishMods")`、`stonecutter tasks { order("publishModrinth") }`。
- **NeoForge/Forge 关键**：每个加载器 build 脚本里需 `tasks.named("createMinecraftArtifacts") { dependsOn("stonecutterGenerate") }`（见 multiloader-build.md）。

---

## 5. build.gradle.kts（每节点模板，重复运行）

`stonecutter` 扩展，别名 `sc`。常用：
- `sc.current.project`：节点目录名（如 `1.20.1-forge`）。`sc.current.project.substringAfterLast('-')` 取 loader。
- `sc.current.version`：逻辑版本（如 `1.20.1`）。
- `sc.current.parsed`：可比较包装。`sc.current.parsed >= "1.21"`、`eq`、`matches(">=1.21 <1.21.5")`（仅 Kotlin DSL 支持运算符重载）。
- 任意字符串运算：`sc.compare("1.21","1.20")`、`sc.eval("1.20", ">=1.21 <1.21.5")`、`sc.parse("...")`。
- 版本属性：`property("deps.forge")` / `findProperty(...)`，来自 `./gradle.properties` 与 `./versions/<node>/gradle.properties`（返回 `Any`，需 `as String`）。

Java 版本映射（来自模板，已核实）：
```kotlin
val requiredJava = when {
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21   // 1.21.1 -> 21
    sc.current.parsed >= "1.18"   -> JavaVersion.VERSION_17   // 1.20.1 -> 17
    sc.current.parsed >= "1.17"   -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}
```

---

## 6. 版本化注释语法（已核实）

适配文件注释风格：Java/Kotlin/JSON5 用 `//` 与 `/* */`（Kotlin 支持嵌套 `/* /* */ */`）；YAML/Properties/AT/AW 用 `#`。

**作用域**：
- 闭合：
  ```java
  //? if >=1.21 {
  /*onlyOn121AndAbove();
  *///?}
  ```
  （未激活时代码被 `/* */` 包裹；激活时解开。）
- 行：`//? if <1.21` 仅作用于下一非空行。
- Lookup：`/*? cond >>*/param`（到首个空白）或 `/*? cond >> ');'*/`（到指定串）、`>>+ '.'`（含定位串）。

**分支**（链中只有最后一支可非闭合）：
```java
//? if 1.20.1 {
 a();
//?} elif 1.21.1 {
/*b();
*///?} else {
/*c();
*///?}
```

**内联**：`method(/*? if >1.20 {*/ newParam /*?}*/)`

**谓词**：`=`(可省略) `!=` `<` `>` `<=` `>=`；`~`（major.minor 相等，如 `~1.20`）；`^`（major 相等）。可链：`//? if ~1.20 <1.20.4`。

**逻辑**：`!`、`||`、`&&`、`()`，如 `//? if forge && (1.20 || 1.21) {`。

**常量**（多加载器核心）：`//? if forge {` / `//? if neoforge {`（由 §4 的 `constants.match` 提供）。

**依赖谓词**（显式目标）：`//? if minecraft: >=1.20 {`、`//? if mod_menu: >=1.0 {`（需 `dependencies["mod_menu"]=...`）。

**嵌套**：最多 10 层；多行注释 `/* */` 会被替换为 `/^ ^/` 以正确配对。

---

## 7. 常量 / swap / 替换（在 `stonecutter {}` / `parameters {}` 内）

- **常量**：`constants["snapshot"] = (findProperty("is_snapshot")=="true")`；选择器 `constants.match(loader, "fabric","neoforge","forge")`（匹配项为 true）。注释用 `//? if <name> {`。
- **swap**（替换整段代码）：
  ```kotlin
  swaps["my_swap"] = when { current.parsed >= "1.21" -> "method1();" else -> "method2();" }
  ```
  注释：`//$ my_swap` 置于目标行上方。支持参数 `$1,$2` 与 `//$ my_swap arg '"str"'`。
- **替换**（跨文件查找替换，版本切换时生效）：
  ```kotlin
  replacements.string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }
  replacements.regex(current.parsed >= "1.21")     { replace("old(\\w+)Ctx" to "new$1Ctx", "new(\\w+)Ctx" to "old$1Ctx") }
  ```
  参数是“方向”（true 正向，false 逆向）。可加标识符 `replacements.string(cond, "me_imports") {...}` 并在文件顶部 `//~ me_imports` 启用；`!` 前缀默认启用/切换。

> 经验法则：能用 `//? if` 表达的差异优先用注释；同一片段在多处重复时用 swap；跨文件统一改名（如 `ResourceLocation`→`Identifier`）用 string 替换；复杂模式才用 regex（更慢）。

---

## 8. Blackboard 具体配置（落地基线）

- 节点：`1.20.1-forge`（Java 17）、`1.21.1-neoforge`（Java 21）。
- 加载器差异隔离：在 `com.tonywww.blackboard.platform` 用 `//? if forge {` / `//? if neoforge {`，或字符串替换处理 1.21 的 API 改名（如 `ResourceLocation`→1.21.x 仍叫 `ResourceLocation`，无需改；但 `Component.Serializer` 等编解码差异用注释隔离）。
- 对外 API（`com.tonywww.blackboard.api.*`）**不写**版本注释，保持稳定。

settings 与控制器见 §3/§4 的 Blackboard 版本；构建脚本见 multiloader-build.md。

---

## 9. 关键陷阱（已核实）

1. **节点名/版本必须分离**（`"1.20.1-forge" to "1.20.1"` 等价于 `version("1.20.1-forge","1.20.1")`），否则版本谓词错乱。
2. **NeoForge/Forge 必须** `createMinecraftArtifacts dependsOn stonecutterGenerate`，否则用的是未预处理源码。
3. build 脚本名不能是 `stonecutter.gradle.kts`。
4. `parameters {}` 只放 Stonecutter 配置，不放依赖/任务。
5. 闭合分支链中只有最后一支能省略 `{}`。
6. 提交前运行 `Reset active version`，避免预处理噪声进入 git。

---

## 修订记录
- 2026-06-30：依据官方 wiki（start/settings、start/builds、start/comments、config/settings、config/controller、config/build、config/params、tips/multiloader）与官方 multiloader 模板、rotgruengelb 模板归档。
