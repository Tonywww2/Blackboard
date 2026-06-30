# 外部参考：KubeJS 集成（自定义事件/绑定）

> 来源：SlashBlade-SenDims 真实源码 `src/main/java/com/tonywww/slashblade_sendims/kubejs/`（KubeJS 6 / Forge 1.20.1），已逐文件核实。
> 用途：实现 [blackboard-design.md](../blackboard-design.md) §10 的 KubeJS 兼容——让脚本注册题目生成器、干预选题、自定义奖励。

目录：
1. 插件发现与入口（核实）
2. 事件组 `EventGroup`（核实）
3. 事件类 `EventJS`（核实）
4. 与 mod 生命周期/总线的桥接（核实）
5. 映射到 Blackboard
6. 版本差异：KubeJS 6（1.20.1）↔ KubeJS 7（1.21.1）——待验证

---

## 1. 插件发现与入口（核实，KubeJS 6 / Forge 1.20.1）

**发现文件**：`src/main/resources/kubejs.plugins.txt`，内容为插件类全限定名：
```
com.tonywww.slashblade_sendims.kubejs.SBSDPlugin
```

**插件入口** `SBSDPlugin.java`：
```java
public class SBSDPlugin extends KubeJSPlugin {
    // 由 mod 主类构造时调用，注册「在 FML 生命周期里 post 事件」的监听器
    public static void register(IEventBus bus) {
        bus.addListener(TierRegisterEventJS::tierRegisterHandler);
    }

    @Override
    public void registerEvents() {
        SBSDEvents.GROUP.register();   // 注册自定义事件组，使 JS 可用
    }
}
```

要点：
- `KubeJSPlugin`（`dev.latvian.mods.kubejs.KubeJSPlugin`）。覆写 `registerEvents()` 注册事件组。
- 额外的 `static register(IEventBus bus)` 不是 KubeJS API，而是该 mod 自己的约定：在 **mod 主类构造器**里调用 `SBSDPlugin.register(modBus)`，把「在某个 FML 生命周期事件里 post 自定义事件」的监听器挂到 mod 总线。

---

## 2. 事件组 `EventGroup`（核实）

`SBSDEvents.java`：
```java
public interface SBSDEvents {
    EventGroup GROUP = EventGroup.of("SBSDEvents");
    EventHandler TierRegister = GROUP.startup("registerTier", () -> TierRegisterEventJS.class);
}
```

- `EventGroup.of("SBSDEvents")`：JS 中以该名访问，如 `SBSDEvents.registerTier(event => {...})`。
- `GROUP.startup("registerTier", () -> XEventJS.class)`：声明一个 **startup 类型** 事件处理器（在 startup 脚本阶段触发）。另有 `GROUP.server(...)`、`GROUP.client(...)` 对应 server/client 脚本（KubeJS 三类脚本）。
- `EventHandler` 实例（如 `TierRegister`）用 `.post(eventObject)` 触发，使对应 JS 回调执行。

---

## 3. 事件类 `EventJS`（核实）

`TierRegisterEventJS.java`（节选）：
```java
public class TierRegisterEventJS extends StartupEventJS {

    @HideFromJS  // 内部重载，不暴露给 JS
    public void registerTier(int level, int uses, float speed, float attackDamageBonus,
                             int enchantmentValue, ResourceLocation tag, Ingredient repairIngredient,
                             List<ResourceLocation> after, ResourceLocation name) { ... }

    @Info("""
            第一个参数为挖掘等级
            第二个是ResourceLocation的方块tag
            ...""")                       // JS 文档/补全
    public void registerTier(int level, ResourceLocation tag, Ingredient repairIngredient,
                             List<ResourceLocation> after, ResourceLocation name) {
        registerTier(level, 520, level * 4 - 7, 0, 30, tag, repairIngredient, after, name);
    }

    // 在 FML 通用初始化阶段 post 本事件，使 startup 脚本里的 SBSDEvents.registerTier(...) 回调执行
    public static void tierRegisterHandler(FMLCommonSetupEvent event) {
        SBSDEvents.TierRegister.post(new TierRegisterEventJS());
    }
}
```

要点：
- 继承 `StartupEventJS`（startup 事件）；server 事件继承 `ServerEventJS` 等。
- **公共方法即 JS API**：JS 中 `event.registerTier(...)` 直接调用。`@Info`/`@Param` 提供类型提示与文档；`@HideFromJS` 隐藏内部重载。
- `XEventJS.handler(FMLxxxEvent)` + `GROUP.X.post(new XEventJS())`：决定事件**何时**触发（这里在 `FMLCommonSetupEvent`）。

JS 用法（startup 脚本）：
```js
SBSDEvents.registerTier(event => {
    event.registerTier(3, "minecraft:mineable/pickaxe", Ingredient.of("minecraft:diamond"), [], "mymod:my_tier")
})
```

---

## 4. 与 mod 生命周期/总线的桥接（核实）

链路：
1. `kubejs.plugins.txt` → KubeJS 实例化 `SBSDPlugin`，调用 `registerEvents()` → `GROUP.register()`（事件组对 JS 可见）。
2. mod 主类构造器 → `SBSDPlugin.register(modBus)` → `modBus.addListener(TierRegisterEventJS::tierRegisterHandler)`。
3. 游戏进入 `FMLCommonSetupEvent` → `tierRegisterHandler` → `GROUP.TierRegister.post(new TierRegisterEventJS())` → 执行 startup 脚本里登记的回调。

> 即：**KubeJS 负责让 JS 能看到事件组**；**mod 自己负责在合适的生命周期 post 事件**。两者通过 `EventGroup`/`EventHandler` 衔接。

---

## 5. 映射到 Blackboard

按 design §10，需要三个 JS 事件：注册生成器（startup）、干预选题（server）、自定义奖励（server）。

```java
// BlackboardKubeEvents.java
public interface BlackboardKubeEvents {
    EventGroup GROUP = EventGroup.of("BlackboardEvents");
    EventHandler REGISTER_GENERATORS = GROUP.startup("registerGenerators", () -> RegisterGeneratorsEventJS.class);
    EventHandler SELECT_GENERATOR    = GROUP.server("selectGenerator",  () -> SelectGeneratorEventJS.class);
    EventHandler REWARD              = GROUP.server("reward",           () -> RewardEventJS.class);
}
```

- `RegisterGeneratorsEventJS extends StartupEventJS`：暴露 `register(id, builderCallback)`，在回调里用 JS 配置 `generate`/`validate`/`weight`/`tag`，内部转成 `QuestionGenerator` 注册进 `BlackboardRegistries`（见 internal-core-api.md）。在 `FMLCommonSetupEvent`（或冻结前的合适时机）`post`。
- `SelectGeneratorEventJS extends ServerEventJS`：包装本模组 `SelectGeneratorEvent`，暴露 `candidates`、`force(id)`。**桥接方式**：在原生 `BlackboardEvents.SELECT_GENERATOR` 监听器里 `BlackboardKubeEvents.SELECT_GENERATOR.post(new SelectGeneratorEventJS(nativeEvent))`，把 JS 改动写回原生事件。
- `RewardEventJS extends ServerEventJS`：包装 `RewardEvent`，暴露 `lootTable`、`extraDrops`，同样桥接。

插件类（Blackboard）：
```java
public class BlackboardKubePlugin extends KubeJSPlugin {
    public static void register(IEventBus bus) {
        bus.addListener(RegisterGeneratorsEventJS::onCommonSetup);  // 在 setup 阶段 post 注册事件
    }
    @Override public void registerEvents() { BlackboardKubeEvents.GROUP.register(); }
}
```
并在 `src/main/resources/kubejs.plugins.txt` 写入 `com.tonywww.blackboard.compat.kubejs.BlackboardKubePlugin`。

> KubeJS 为**软依赖**：`compat/kubejs/` 仅在检测到 KubeJS 时编译/加载；`kubejs.plugins.txt` 仅在打包含 KubeJS 的环境时被读取（无 KubeJS 时该文件被忽略，不影响运行）。

---

## 6. 版本差异：KubeJS 6（1.20.1）↔ KubeJS 7（1.21.1）

上文 **基于 KubeJS 6 / Forge 1.20.1**（SlashBlade-SenDims）。据 **KubeJS 官方 README**（已核实），KubeJS 7（1.21.1）**核心模式基本不变**：

**保持一致**：
- **插件发现仍是 `src/main/resources/kubejs.plugins.txt`**（内容为插件 FQN，可在末尾加 mod id，如 `com.x.MyPlugin mymod`）。
- `KubeJSPlugin` 仍是插件基类，钩子含 `init`/`afterInit`/`registerClasses`/`registerBindings`/`registerWrappers`/`registerRecipeTypes`/**`registerEvents`**（注册自定义 `EventGroup`，使脚本可访问）/`attach(Player|World|Server)Data`。
- **`EventGroup` 事件系统保留**（`registerEvents` 里注册自定义事件组）。

**差异（落地确认）**：
- 插件基类包名：KJS7 为 **`dev.latvian.mods.kubejs.plugin.KubeJSPlugin`**（KJS6 为 `dev.latvian.mods.kubejs.KubeJSPlugin`）。
- Gradle 接入（README 确认）：仓库 `maven("https://maven.latvian.dev/releases")`（group `dev.latvian.mods`/`dev.latvian.apps`）+ jitpack（`com.github.rtyley`）；依赖 `dev.latvian.mods:kubejs-neoforge:<ver>`（1.20.1 用 `kubejs-forge`）。版本规则：按目标 MC 取最新（如 1.18.2 → `1802.+`；1.21.1 → 对应最新 `kubejs-neoforge`）。还需 `rhino`（KubeJS 的 JS 引擎依赖）。
- `EventHandler`/`EventJS` 基类（KJS6 的 `StartupEventJS`/`ServerEventJS`）在 7.x 的精确包名与方法签名**需查 KJS7 源码确认**（事件系统在 7.x 有重构）。
- 取 mod 总线与 `post` 时机在 NeoForge 下的等价写法。

> 结论：**1.20.1（KJS6）与 1.21.1（KJS7）共用同一套思路**（`kubejs.plugins.txt` + `KubeJSPlugin.registerEvents` + `EventGroup`），仅基类包名/Gradle 坐标/部分事件签名不同。用 Stonecutter `//? if forge`/`//? if neoforge` 隔离这些差异即可；KJS7 的 `EventGroup`/`EventJS` 精确签名在落地 1.21 侧时查 KJS7 源码补全。

---

## 修订记录
- 2026-06-30：依据 SlashBlade-SenDims（KubeJS 6 / Forge 1.20.1）的 `kubejs.plugins.txt`、`SBSDPlugin`、`SBSDEvents`、`TierRegisterEventJS` 归档插件/事件组/事件类模式与生命周期桥接。
- 2026-06-30：依据 KubeJS 官方 README 补充 KubeJS 7（1.21.1）：发现机制仍为 `kubejs.plugins.txt`、基类 `dev.latvian.mods.kubejs.plugin.KubeJSPlugin`、`registerEvents`/`EventGroup` 保留、Gradle 经 `maven.latvian.dev` 用 `kubejs-<loader>`；仅 `EventGroup`/`EventJS` 精确签名待 KJS7 源码确认。
