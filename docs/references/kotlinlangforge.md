# 外部参考：KotlinLangForge（Kotlin 语言适配器）

> 来源：https://github.com/btwonion/KotlinLangForge（README，已核实）。
> 作用：为 **Forge / NeoForge** 提供 Kotlin 语言适配器与 Kotlin 运行时库，使模组主类与代码可用 Kotlin 编写。
> 构建侧接入见 [multiloader-build.md](multiloader-build.md)。

目录：
1. 元数据声明（mods.toml）
2. 模组主类入口
3. 事件总线与注解
4. 版本对照与坐标
5. 内置库
6. Blackboard 用法基线
7. 待验证项

---

## 1. 元数据声明（mods.toml / neoforge.mods.toml）

在 `(neoforge.)mods.toml` 顶部声明用 KLF 作为语言加载器：

```toml
modLoader = "klf"
loaderVersion = "[1,)"
```

- Forge 1.20.1：`src/main/resources/META-INF/mods.toml`
- NeoForge 1.21.1：`src/main/resources/META-INF/neoforge.mods.toml`

其余 `[[mods]]`、`[[dependencies.<id>]]` 照常写（见 multiloader-build.md §6）。

> ⚠️ **KLF 是 LANGPROVIDER，不是 MOD**：`modLoader = "klf"` + `loaderVersion` 即已要求 klf 语言提供者；**切勿**再写 `modId = "klf"` 的 `[[dependencies]]`。否则 dev 运行时报 `Missing mandatory dependencies: Mod ID 'klf' ... [MISSING]` 无法进游戏（已踩坑，见 multiloader-build.md §9.2）。

---

## 2. 模组主类入口

> `@Mod` 类必须是 **object** 或**带 public 构造器的 class**。构造器可接收以下参数（不可重复），按需声明：
> - `IEventBus`
> - `ModContainer`
> - `KotlinModContainer`
> - `Dist`

Kotlin object 形式（推荐）：

```kotlin
package com.tonywww.blackboard

import net.neoforged.fml.common.Mod      // NeoForge 包名；Forge 1.20.1 为 net.minecraftforge.fml.common.Mod（用 //? 隔离）
import net.neoforged.bus.api.IEventBus

@Mod(Blackboard.MOD_ID)
object Blackboard {
    const val MOD_ID = "blackboard"

    // KLF 会注入构造/init 所需对象；object 形式可在 init 块或通过参数化 class 获取 IEventBus
    fun init(modBus: IEventBus) {
        // 注册内容、监听 mod bus 事件等
    }
}
```

带构造器的 class 形式（可直接拿到 bus）：

```kotlin
@Mod(Blackboard.MOD_ID)
class Blackboard(modBus: IEventBus, container: ModContainer, dist: Dist) {
    init {
        // modBus.addListener(::onCommonSetup) 等
    }
    companion object { const val MOD_ID = "blackboard" }
}
```

> `@Mod`、`IEventBus`、`ModContainer`、`Dist` 的**包名在 Forge 1.20.1 与 NeoForge 1.21.1 不同**（`net.minecraftforge.*` vs `net.neoforged.*`）。用 Stonecutter `//? if forge { ... //?} else { ... //?}` 或 import 替换隔离。→ 该差异属平台边界，见 [internal-core-api.md](internal-core-api.md) 与 stonecutter.md。

---

## 3. 事件总线与注解

- **Mod 总线**：顶层声明 `dev.nyon.klf.MOD_BUS`（无需手动传递即可访问）。
- **自动事件注册**：在类/文件上加 `@EventBusSubscriber`，KLF 自动扫描参数含事件的方法并判定其所属总线。
- 可用 `@SubscribeEvent` 调整监听器参数。
- **Forge 注意**：私有事件监听器在 Forge 上无法处理，会崩溃（监听器方法不要用 `private`）。

```kotlin
import dev.nyon.klf.MOD_BUS
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber

@EventBusSubscriber
object ModEvents {
    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {   // 具体事件类型按版本/加载器，见外部参考 C4
        // 作答解析入口
    }
}
```

> 本模组自有的 `BlackboardEvents`（见 internal-core-api.md §3）是**独立的轻量总线**，与 Forge/NeoForge 的 mod/game bus 无关；二者并存：KLF 用于挂接原版/加载器事件（聊天、生命周期），`BlackboardEvents` 用于对外开发者 API。

> ⚠️ **NeoForge + Loom dev：游戏总线 `@EventBusSubscriber` 会崩**。KLF 的 langprovider 在 FML 的 PLUGIN 模块层运行，其 `AutomaticEventSubscriber` 处理**游戏总线**事件时会调 `getGameBus()`→`NeoForge.EVENT_BUS`（GAME 层类），PLUGIN 层读不到 GAME 层 → `IllegalAccessError: module klf does not read module neoforge`（mod 构造期崩）。**mod 总线**订阅（如 datagen `GatherDataEvent`）走 `getModBus`、不受影响。
> 生产环境不崩（生产的 PLUGIN 层能读到 neoforge 平台类），仅 **Loom dev** 的模块拓扑触发。
> **对策**：NeoForge 上游戏总线订阅**不要**用 `@EventBusSubscriber`，改在 GAME 层的 mod 构造代码里手动 `NeoForge.EVENT_BUS.register(obj)`；用 Stonecutter 让注解**仅 Forge 保留**。Forge 无此问题（无模块层隔离），照常用 `@EventBusSubscriber`。详见 [multiloader-build.md](multiloader-build.md) §9.4、[loader-platform-api.md](loader-platform-api.md) §6 C4。

---

## 4. 版本对照与坐标（已核实）

「language provider 版本」(lp) 是 KLF 自有的区分标识：

| Minecraft | lp | 加载器 |
| --- | --- | --- |
| 1.16.5 | 1.0 | Forge |
| 1.17.1 – 1.20.4 | **2.0** | Forge, NeoForge |
| 1.20.5 – 1.21.8 | **3.0** | NeoForge |
| 1.21.9 – 26.x | 3.1 | NeoForge |

**本项目**：
- Forge **1.20.1** → lp **2.0**，loader **forge**。
- NeoForge **1.21.1** → lp **3.0**，loader **neoforge**。

Maven（Architectury Loom，用 `modImplementation`）：
```kotlin
repositories { maven("https://repo.nyon.dev/releases") }

dependencies {
    // 坐标格式：dev.nyon:KotlinLangForge:<klf版本>-k<kotlin版本>-<lp>+<loader>
    // Forge 1.20.1：
    modImplementation("dev.nyon:KotlinLangForge:2.12.1-k2.4.0-2.0+forge")
    // NeoForge 1.21.1：
    modImplementation("dev.nyon:KotlinLangForge:2.12.1-k2.4.0-3.0+neoforge")
}
```

- **选定稳定版本 `2.12.1`**（非 beta，内置 Kotlin `2.4.0`；编译用 Kotlin 版本应与之一致）。已在 `repo.nyon.dev/releases` 确认两变体 POM 均存在：`2.12.1-k2.4.0-2.0+forge`、`2.12.1-k2.4.0-3.0+neoforge`。
- **依赖配置名 = `modImplementation(...)`**（已确认）：KLF 自身的 testmod 用 **Architectury Loom**（`dev.architectury.loom`），`modImplementation` 为 Loom 提供。因此本项目也用 Architectury Loom（见 [multiloader-build.md](multiloader-build.md)），不用 ModDevGradle。

---

## 5. 内置库（KLF 已提供，勿重复打包）

KLF 随包提供（版本以 KLF release 为准）：
- `kotlin-stdlib` / `-jdk8` / `-jdk7`、`kotlin-reflect`
- `kotlinx-serialization-core/json/cbor`
- `kotlinx-coroutines-core/jdk8`
- `kotlinx-datetime`、`kotlinx-io-core/bytestring`、`atomicfu`

> 因此模组**不应**再把这些塞进自己的 jar；编译期可用，运行期由 KLF 提供。

> ⚠️ **Forge 1.20.1 dev 运行的 stdlib 陷阱**：Loom 会把 KLF 传递来的 `kotlin-stdlib` 重映射成一份「保留 `Multi-Release: true` 清单标志、却丢了 `META-INF/versions/`」的 jar，使 Forge 1.20.1 的 securejarhandler 在 `runClient` 时崩溃（`UnionFileSystem$NoSuchFileException: /META-INF/versions`）。修复：在 KLF 的 `modImplementation` 上 `exclude` 掉 `org.jetbrains.kotlin:kotlin-stdlib`（Kotlin 插件已提供正版）。详见 multiloader-build.md §9.1。

> ⚠️ **NeoForge 1.21.1 dev 运行的 stdlib 陷阱（与 Forge 相反）**：KLF 的 langprovider 在 FML 的 PLUGIN 模块层，读不到 Loom 放在 game classpath 的重映射 Kotlin，实例化时 `NoClassDefFoundError: kotlin/Pair` → FML 报 `Missing language klf`。**需**对 NeoForge 用 `forgeRuntimeLibrary` 把 kotlin-stdlib+kotlin-reflect 补到 dev 运行期 classpath（与 Forge 的 `exclude` 相反）。详见 multiloader-build.md §9.3。

---

## 6. Blackboard 用法基线

1. 两个 `mods.toml` 均设 `modLoader = "klf"`、`loaderVersion = "[1,)"`。
2. 主类 `com.tonywww.blackboard.Blackboard`（object，`@Mod("blackboard")`）。
3. 用 `//? if forge { ... //?} else { ... //?}` 隔离 `net.minecraftforge.*` 与 `net.neoforged.*` 的 import / API。
4. 用 `@EventBusSubscriber` + `@SubscribeEvent` 挂接服务端聊天事件（作答入口）与生命周期（注册/冻结时机，见 internal-core-api.md §10）。监听器方法**非 private**。**但 NeoForge + Loom dev 下游戏总线订阅须改手动注册**（见 §3 警告；本项目 `PlatformEvents` 即如此处理）。
5. 依赖 KLF 运行时，不重复打包 Kotlin 库。

---

## 7. 待验证项

1. ~~ModDevGradle 下的依赖配置名~~ ✅ **已解决**：本项目改用 **Architectury Loom**（与 KLF testmod 一致），配置名为 `modImplementation(...)`。见 [multiloader-build.md](multiloader-build.md)。
2. **`@Mod`/`IEventBus` 等包名**在 1.20.1-forge 与 1.21.1-neoforge 的精确差异（Forge `net.minecraftforge.fml.*` vs NeoForge `net.neoforged.fml.*` / `net.neoforged.bus.*`）——导入后查反编译源码确认。
3. KLF object 形式如何取得 `IEventBus`（构造器参数仅对 class 形式直观；object 形式可能依赖 `MOD_BUS` 顶层声明）——以实际 API 为准。
4. KLF/Kotlin 具体版本号（用各加载器对应的最新 KLF release）。

---

## 修订记录
- 2026-06-30：依据 KotlinLangForge README 归档（mods.toml、入口、事件总线、版本表、坐标、内置库）。标注 §7 待验证项。
- 2026-07-01：补两条 dev 运行实测要点——§1「KLF 是 LANGPROVIDER 不是 MOD，勿写 klf 依赖」、§5「Loom 重映射的 kotlin-stdlib 缺 `META-INF/versions/` 会崩 Forge 1.20.1 runClient，需 `exclude`」。详见 multiloader-build.md §9。
- 2026-07-01（补 NeoForge）：§3 补「NeoForge + Loom dev 游戏总线 `@EventBusSubscriber` 触 `getGameBus` 跨层 IllegalAccessError——改手动注册、注解仅 Forge 保留」警告；§5 补「NeoForge langprovider 在 PLUGIN 层读不到 game classpath 的 Kotlin → `Missing language klf`，需 `forgeRuntimeLibrary` 补 stdlib+reflect」；§6-4 补手动注册提醒。详见 multiloader-build.md §9.3-9.4。
