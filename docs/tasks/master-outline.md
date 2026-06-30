# Blackboard 主设计大纲与任务分解

> 本文件是实现阶段的**总纲**：完整项目结构 + 每个文件/类的职责。
> 设计依据：[../blackboard-design.md](../blackboard-design.md)。实装细节依据 `docs/references/` 下各参考：
> [internal-core-api](../references/internal-core-api.md)、[answer-format-and-validation](../references/answer-format-and-validation.md)、[stonecutter](../references/stonecutter.md)、[multiloader-build](../references/multiloader-build.md)、[kotlinlangforge](../references/kotlinlangforge.md)、[loader-platform-api](../references/loader-platform-api.md)、[kubejs-integration](../references/kubejs-integration.md)。

约定标记：
- 🟦 **纯 Kotlin（加载器无关）**：单一实现，无版本差异。
- 🟨 **含平台边界**：用 Stonecutter `//? if forge {` / `//? if neoforge {` 隔离两版差异。
- 🟥 **待决策/待反编译**：依赖 design §13 拍板，或导入后查反编译源码确认（C4/C5/C6）。
- 构建采用 **Architectury Loom（flat）+ Stonecutter**（见 multiloader-build.md，已由 ModDevGradle 更正）。

---

## 0. 包结构总览

- `com.tonywww.blackboard.api.*` — **对外稳定 API**（其他模组依赖此处；不出现版本差异）。
- `com.tonywww.blackboard.core.*` — **内部实现**（选题/判题/奖励/持久化/管理；不对外承诺）。
- `com.tonywww.blackboard.content.*` — 方块、方块实体、物品、注册。
- `com.tonywww.blackboard.chat.*` — 默认作答格式与聊天处理。
- `com.tonywww.blackboard.validation.*` — 内置校验器（可选辅助）。
- `com.tonywww.blackboard.builtin.*` — 内置生成器与黑板类型。
- `com.tonywww.blackboard.platform.*` — 加载器/版本边界封装。
- `com.tonywww.blackboard.compat.kubejs.*` — KubeJS 插件（软依赖）。
- `com.tonywww.blackboard.client.*` — 客户端初始化（AUI 渲染器注入，**后续**）。

---

## 1. 完整项目结构

```
Blackboard/
├─ settings.gradle.kts
├─ stonecutter.gradle.kts
├─ build.gradle.kts                       # 单一共享脚本（flat Loom）
├─ gradle.properties                      # mod.* 元数据 + Gradle 选项
├─ gradle/
│  └─ libs.versions.toml                  # kotlin / architectury-loom 版本目录
├─ versions/
│  ├─ 1.20.1-forge/gradle.properties      # vers.mcVersion / vers.deps.fml / loom.platform / deps.klf
│  └─ 1.21.1-neoforge/gradle.properties
├─ docs/ …
└─ src/main/
   ├─ kotlin/com/tonywww/blackboard/
   │  ├─ Blackboard.kt                     # @Mod 入口
   │  ├─ api/
   │  │  ├─ BlackboardApi.kt               # MOD_ID / id() / BlackboardTags
   │  │  ├─ question/
   │  │  │  ├─ Question.kt
   │  │  │  ├─ Questions.kt                # 题目 Builder 工厂
   │  │  │  ├─ QuestionGenerator.kt
   │  │  │  ├─ GenerationContext.kt
   │  │  │  ├─ AnswerContext.kt
   │  │  │  └─ AnswerResult.kt
   │  │  ├─ board/
   │  │  │  ├─ BlackboardType.kt
   │  │  │  ├─ GeneratorPool.kt
   │  │  │  ├─ SelectionContext.kt
   │  │  │  ├─ RewardContext.kt
   │  │  │  └─ WeightedGenerator.kt
   │  │  ├─ event/
   │  │  │  ├─ EventHook.kt
   │  │  │  ├─ BlackboardEvents.kt
   │  │  │  ├─ SelectGeneratorEvent.kt
   │  │  │  ├─ QuestionGeneratedEvent.kt
   │  │  │  ├─ AnswerEvent.kt
   │  │  │  └─ RewardEvent.kt
   │  │  ├─ registry/
   │  │  │  ├─ SimpleRegistry.kt
   │  │  │  └─ BlackboardRegistries.kt
   │  │  ├─ render/
   │  │  │  ├─ BlackboardRenderer.kt
   │  │  │  ├─ RenderContext.kt
   │  │  │  └─ BlackboardRendering.kt
   │  │  └─ chat/
   │  │     ├─ AnswerFormat.kt
   │  │     └─ ParsedAnswer.kt
   │  ├─ core/
   │  │  ├─ QuestionImpl.kt
   │  │  ├─ Contexts.kt                    # 各上下文实现
   │  │  ├─ Selection.kt                   # selectGenerator()
   │  │  ├─ Reward.kt                      # defaultReward()
   │  │  ├─ AnswerHandler.kt               # 作答主流程
   │  │  ├─ BlackboardManager.kt           # boardId 索引 / 冻结生命周期
   │  │  └─ QuestionNbt.kt                 # Question ↔ NBT
   │  ├─ content/
   │  │  ├─ SyncedBlockEntity.kt
   │  │  ├─ BlackboardBlockEntity.kt
   │  │  ├─ BlackboardBlock.kt
   │  │  ├─ ModBlocks.kt
   │  │  ├─ ModBlockEntities.kt
   │  │  └─ ModItems.kt
   │  ├─ chat/
   │  │  ├─ DefaultAnswerFormat.kt
   │  │  └─ ChatHandler.kt
   │  ├─ validation/
   │  │  └─ Validators.kt
   │  ├─ builtin/
   │  │  ├─ BuiltinGenerators.kt
   │  │  └─ BuiltinBlackboardTypes.kt
   │  ├─ platform/
   │  │  ├─ PlatformComponents.kt
   │  │  ├─ PlatformRegistration.kt
   │  │  ├─ PlatformEvents.kt
   │  │  └─ PlatformLoot.kt
   │  ├─ compat/kubejs/
   │  │  ├─ BlackboardKubePlugin.kt
   │  │  ├─ BlackboardKubeEvents.kt
   │  │  └─ events/
   │  │     ├─ RegisterGeneratorsEventJS.kt
   │  │     ├─ SelectGeneratorEventJS.kt
   │  │     └─ RewardEventJS.kt
   │  └─ client/
   │     └─ BlackboardClient.kt            # 后续：注入 AUI 渲染器
   └─ resources/
      ├─ META-INF/mods.toml                # Forge 元数据（modLoader=klf）
      ├─ META-INF/neoforge.mods.toml       # NeoForge 元数据（modLoader=klf）
      ├─ kubejs.plugins.txt                # KubeJS 插件 FQN
      ├─ pack.mcmeta
      ├─ assets/blackboard/                # blockstates/models/textures/lang
      └─ data/blackboard/loot_tables/rewards/default.json
```

> AUI 静态资源目录 `apricity/blackboard/` 在接入 ApricityUI 时再加，本阶段不创建（仅传 `Component`）。

---

## 2. 构建与元数据文件

**settings.gradle.kts** 🟦
- `pluginManagement` 仓库：architectury / fabricmc / minecraftforge / neoforged / kikugie。
- 应用 `dev.kikugie.stonecutter 0.9.6`；`stonecutter { kotlinController = true; create(rootProject) { version("1.20.1-forge","1.20.1"); version("1.21.1-neoforge","1.21.1"); vcsVersion = "1.21.1-neoforge" } }`。
- 参考：[multiloader-build §3](../references/multiloader-build.md)、[stonecutter §3](../references/stonecutter.md)。

**stonecutter.gradle.kts** 🟦
- `stonecutter active "1.21.1-neoforge"`；`parameters { val loader = current.project.substringAfterLast('-'); constants { match(loader, "forge", "neoforge") } }`。
- 参考：[multiloader-build §4](../references/multiloader-build.md)、[stonecutter §4](../references/stonecutter.md)。

**build.gradle.kts** 🟨（单脚本按 `loom.platform` 分支）
- 应用 `libs.plugins.kotlin` + `libs.plugins.architectury.loom`；`minecraft`/`mappings(officialMojangMappings)`；按 `ModPlatform` 选 `forge`/`neoForge` 依赖；`modImplementation` 接入 KLF；`modCompileOnly` 接入 KubeJS（软依赖）；`processResources` 按加载器排除另一份 `*.mods.toml`；Java 17/21。
- 参考：[multiloader-build §5/§7](../references/multiloader-build.md)。

**gradle.properties**（根）🟦 — `mod.id/name/version/group` + `org.gradle.jvmargs`。
**gradle/libs.versions.toml** 🟦 — `kotlin=2.4.0`、`architectury-loom=1.11-SNAPSHOT`。
**versions/1.20.1-forge/gradle.properties** 🟦 — `vers.mcVersion=1.20.1`、`vers.deps.fml=47.4.4`、`loom.platform=forge`、`deps.klf=2.12.1-k2.4.0-2.0+forge`。
**versions/1.21.1-neoforge/gradle.properties** 🟦 — `vers.mcVersion=1.21.1`、`loom.platform=neoforge`、`deps.klf=2.12.1-k2.4.0-3.0+neoforge`；🟥 `vers.deps.fml=21.1.x`（NeoForge 版本待定，取最新 21.1.x）。

---

## 3. 源码：`api/`（对外稳定层，🟦 除特别标注）

**api/BlackboardApi.kt**
- `const val MOD_ID = "blackboard"`；`fun id(path): ResourceLocation`（🟨 `ResourceLocation` 构造两版不同，封装于此）；`object BlackboardTags { DEFAULT/MATH/TEXT }`。
- 参考：[internal-core-api §1](../references/internal-core-api.md)。

**api/question/Question.kt** — `interface Question`：`generatorId`、`content: Component`、`data: CompoundTag`、`prompt: Component?`，便捷读取 `getInt/getDouble/getString/getBoolean`。设计 §5.1。

**api/question/Questions.kt** — `object Questions { fun builder(id): QuestionBuilder }` + `class QuestionBuilder`（`content/prompt/store(...)/build`）。落地入口名为 `Questions.builder`（设计文档示例写 `Question.builder`，按 internal §4.1 修订）。

**api/question/QuestionGenerator.kt** — `class QuestionGenerator`（`id/generate/validate/weight/tags`）+ `Builder`（`generate/validate/weight/tag/build`，`requireNotNull` 校验）。设计 §5.4。

**api/question/GenerationContext.kt** — `interface`：`level: ServerLevel`、`pos`、`blockState`、`blackboard`、`random`、`player?`、`difficulty`。设计 §5.3。

**api/question/AnswerContext.kt** — `interface`：`player`、`level`、`pos`、`blockState`、`text`。设计 §5.3。

**api/question/AnswerResult.kt** — `sealed interface`：`Correct(score, feedback?)` / `Incorrect(feedback?)` / `Invalid(feedback?)` + `companion`（`correct/incorrect/invalid`）。设计 §5.3。

**api/board/BlackboardType.kt** — `class BlackboardType`（`id/pool/selector/onSolved/onFailed/rewardLootTable/answerFormat/maxAttempts`）+ `Builder`。设计 §5.5。（已移除 `regenerateOnSolved`——答对后默认销毁方块，见 §13。）

**api/board/GeneratorPool.kt** — `sealed interface`：`ByTag/Explicit/All` + `fun GeneratorPool.resolve(reg): List<QuestionGenerator>`。设计 §5.5 / internal §4.3。

**api/board/SelectionContext.kt** — `interface`：`blackboard/level/pos/blockState/player?`。设计 §6.1。

**api/board/RewardContext.kt** — `data class`：`level/pos/blockState/player/question/result/blackboard`。internal §7。

**api/board/WeightedGenerator.kt** — `data class WeightedGenerator(generator, var weight)`。设计 §6.1。

**api/event/EventHook.kt** — `class EventHook<T>(debugName)`：`register(listener)` / `invoke(event)`（`CopyOnWriteArrayList`，逐监听器 try/catch 记日志）。internal §3。

**api/event/BlackboardEvents.kt** — `object`：`SELECT_GENERATOR / QUESTION_GENERATED / ANSWER / REWARD`。设计 §6。

**api/event/SelectGeneratorEvent.kt** — `class`（`context`、`candidates: MutableList<WeightedGenerator>`、`var forced`）。设计 §6.1。

**api/event/QuestionGeneratedEvent.kt** — `class`（`level/pos/blockState/question/player?`）。设计 §6.3。

**api/event/AnswerEvent.kt** — `class`（`level/pos/blockState/player/question/result`）。设计 §6.3。

**api/event/RewardEvent.kt** — `class`（`level/pos/blockState/player/question/result`、`var lootTable`、`extraDrops`）。设计 §6.2。

**api/registry/SimpleRegistry.kt** — `class SimpleRegistry<T>`：`LinkedHashMap` 保序、`tagIndex`、`register`（重复抛错）、`get/all/ids/byTag/idOf`、`freeze/isFrozen`，加锁。internal §2。

**api/registry/BlackboardRegistries.kt** — `object`：`QUESTION_GENERATORS`、`BLACKBOARD_TYPES`、`freezeAll()`，含 `register(gen)` 便捷重载。internal §2。

**api/render/BlackboardRenderer.kt** — `fun interface BlackboardRenderer { fun render(ctx: RenderContext) }`。设计 §5.2。
**api/render/RenderContext.kt** — `interface`：`level: Level`、`pos`、`blockState`、`content: Component`。设计 §5.2。
**api/render/BlackboardRendering.kt** — `object { var renderer = No-op }`。设计 §5.2。

**api/chat/AnswerFormat.kt** — `fun interface AnswerFormat { fun parse(message): ParsedAnswer? }`。设计 §7.1 / answer-format §1。
**api/chat/ParsedAnswer.kt** — `data class ParsedAnswer(boardId, answer)`。answer-format §1。

---

## 4. 源码：`core/`（内部实现，🟦 除标注）

**core/QuestionImpl.kt** — `internal class QuestionImpl : Question`（`get*` 读 `data`）；`QuestionBuilder.build()` 产出之。internal §4.1。

**core/Contexts.kt** — `GenerationContextImpl` / `AnswerContextImpl` / `SelectionContextImpl`（data class 实现各上下文接口）。internal §5。

**core/Selection.kt** — `fun selectGenerator(type, ctx): QuestionGenerator`：`pool.resolve` → `WeightedGenerator` 列表 → 广播 `SELECT_GENERATOR` → `forced` 优先 → `type.selector(candidates, ctx)`；`weightedRandomSelect` 用 `ctx.level.random` 加权随机。internal §6（**采用 internal 的权威签名，废弃设计 §6.1 的 `withCandidates` 示意**）。

**core/Reward.kt** 🟨 — `fun defaultReward(rc)`：roll `rewardLootTable` → 广播 `REWARD`（开发者改 `lootTable`/`extraDrops`）→ 发放（背包满则掉落）。采用 internal §7「简化版」合并规则。战利品 roll / 给物为平台边界（→ PlatformLoot）。internal §7。

**core/AnswerHandler.kt** — `fun handleAnswer(player, boardId, text)`：经 `BlackboardManager` 定位黑板 → 组 `AnswerContext` → `gen.validate` → 按 `AnswerResult` 三态处理（`Correct`→ `be.onSolved`（默认：奖励 + **销毁方块**，可重写）；`Incorrect`→`attempts++`+上限策略；`Invalid`→提示不计次）→ 广播 `ANSWER`。answer-format §10、设计 §7.2。🟥 达上限行为依 §13(2)。

**core/BlackboardManager.kt** — 维护 `boardId → BlockPos`（按维度）索引（随区块加载/卸载更新）；触发出题（调 `selectGenerator`+`generate`+广播 `QUESTION_GENERATED`+持久化+标记同步）；注册表冻结时机的协调。internal §10、answer-format §3。🟥 boardId 策略依 §13。

**core/QuestionNbt.kt** 🟨 — `Question.toNbt()` / `questionFromNbt(tag)`：键 `Generator/Content/Prompt/Data`；`Content/Prompt` 经 `PlatformComponents` 编解码。客户端同步只下发 `Generator+Content`（不含 `Data`，防作弊）。internal §9。

---

## 5. 源码：`content/`（方块/方块实体/物品）

**content/SyncedBlockEntity.kt** 🟨 — 同步样板基类：`getUpdatePacket()=ClientboundBlockEntityDataPacket.create(this)`；`getUpdateTag()`（1.20）/ `getUpdateTag(HolderLookup.Provider)`（1.21）；1.20 重写 `onDataPacket`；`inventoryChanged()=setChanged()+sendBlockUpdated`。loader-platform §2。

**content/BlackboardBlockEntity.kt** 🟨 — 继承 `SyncedBlockEntity`。字段：`blackboardTypeId`、当前 `Question`（每板一题、全员共享）、`attempts`、`boardId`。`saveAdditional/load`（🟨 1.21 带 registries，见 §3）经 `QuestionNbt`；服务端出题/作答钩子；客户端 `content` 变化时调 `BlackboardRendering.renderer.render(...)`。**`open fun onSolved(player, result)`** 默认 = `type.onSolved(rc)`（奖励）+ `level.destroyBlock(pos, false)`，子类可重写。设计 §11、internal §9、loader-platform §2/§3。

**content/BlackboardBlock.kt** 🟦/🟨 — `BaseEntityBlock`；朝向 `BlockState` 属性（`HorizontalDirectionalBlock.FACING`）；`newBlockEntity`；放置时分配 `boardId`/登记索引；右键交互（可选，依 §13(c)）。设计 §3。`getCloneItemStack` 形参 1.20/1.21 不同 🟨。

**content/ModBlocks.kt** 🟨 — `DeferredRegister<Block>`（🟨 句柄 `ForgeRegistries.BLOCKS` / `Registries.BLOCK`）；`BLACKBOARD: Supplier<Block>`。字段统一 `Supplier<T>`。loader-platform §1。

**content/ModBlockEntities.kt** 🟨 — `DeferredRegister<BlockEntityType<?>>`（🟨 `BLOCK_ENTITY_TYPES`/`BLOCK_ENTITY_TYPE`）；`BLACKBOARD_BE = BlockEntityType.Builder.of(::BlackboardBlockEntity, BLACKBOARD.get()).build(null)`。loader-platform §1。

**content/ModItems.kt** 🟨 — `DeferredRegister<Item>`（🟨 `ITEMS`/`ITEM`）；`BLACKBOARD_ITEM = BlockItem(BLACKBOARD.get(), props)`；可加创造模式标签页。loader-platform §1。

---

## 6. 源码：`chat/` + `validation/`

**chat/DefaultAnswerFormat.kt** 🟦 — `object : AnswerFormat`：前缀 `!ans`（大小写不敏感）、首 token 为 `boardId`、其余为 `answer`（两端 trim）。含边界用例。answer-format §2。🟥 默认格式细节依 §13(1)。

**chat/ChatHandler.kt** 🟨（订阅经 platform）— 接收服务端聊天文本 → 用当前 `AnswerFormat.parse` → 命中则路由 `AnswerHandler.handleAnswer` → 可选拦截（不广播）。answer-format §2、设计 §7。🟥 是否拦截依 §13(2)；🟥 `ServerChatEvent` 两版写法 C4（导入后查反编译）。

**validation/Validators.kt** 🟦 — `text/textRegex/number/matrix/expression` 校验器 + `parseNumber/parseMatrix/matricesEqual/normalizeExpr` + `ExpressionEvaluator` 接口（不绑定具体库）。answer-format §5–§9。说明：模组核心**不内置**数值/CAS 库，`Validators` 为可选辅助，作者可在 `validate` lambda 自行实现。

---

## 7. 源码：`builtin/`

**builtin/BuiltinGenerators.kt** 🟦 — 注册内置生成器（加/减/乘/除、平方、简单文字题等），`tag(BlackboardTags.MATH/TEXT)`，用 `Validators` 或内联判题。设计 §5.4。

**builtin/BuiltinBlackboardTypes.kt** 🟦 — 注册 `DEFAULT_TYPE`（`pool=ByTag(DEFAULT)`、`selector=weightedRandomSelect`、`onSolved=defaultReward`、`answerFormat=DefaultAnswerFormat`、`rewardLootTable=id("rewards/default")`）。internal §4.3。

---

## 8. 源码：`platform/`（边界封装，🟨/🟥）

**platform/PlatformComponents.kt** 🟥 — `serialize(Component): String` / `deserialize(String): Component`。1.20.1 `Component.Serializer.toJson/fromJson`；1.21.x 需 registries（`ComponentSerialization.CODEC` + `RegistryOps`）。C6，导入后查反编译源码确认。loader-platform §6。

**platform/PlatformRegistration.kt** 🟨 — 封装 `DeferredRegister.create(<句柄>, MOD_ID)` 与 `register(bus)`；供 `ModBlocks/ModBlockEntities/ModItems` 复用，集中隔离 import 与句柄。loader-platform §1。

**platform/PlatformEvents.kt** 🟨/🟥 — 经 KLF `@EventBusSubscriber` 订阅：服务端聊天事件 → `ChatHandler`（🟥 C4）；生命周期（通用初始化/服务器启动）→ `BlackboardRegistries.freezeAll()` 与 `BlackboardManager` 初始化。kotlinlangforge §3、internal §10。

**platform/PlatformLoot.kt** 🟥 — `rollLootTable(level, player, pos, table): List<ItemStack>` 与 `giveOrDrop(player, stack)`。`LootTable`/`LootParams` 两版差异（C5），导入后查反编译源码确认。loader-platform §6。

---

## 9. 源码：`compat/kubejs/`（软依赖，🟨）

**compat/kubejs/BlackboardKubePlugin.kt** 🟨 — `class : KubeJSPlugin`（🟨 KJS6 `dev.latvian.mods.kubejs.KubeJSPlugin`，KJS7 `…kubejs.plugin.KubeJSPlugin`）：`registerEvents() → BlackboardKubeEvents.GROUP.register()`；`static register(bus)` 挂生命周期监听以 `post` 注册事件。kubejs-integration §1/§5/§6。

**compat/kubejs/BlackboardKubeEvents.kt** 🟨 — `EventGroup.of("BlackboardEvents")` + `REGISTER_GENERATORS(startup)` / `SELECT_GENERATOR(server)` / `REWARD(server)`。kubejs-integration §5。

**compat/kubejs/events/RegisterGeneratorsEventJS.kt** — startup 事件：JS `register(id, builder)` → 组装 `QuestionGenerator` 注册进 `BlackboardRegistries`。kubejs-integration §5。
**compat/kubejs/events/SelectGeneratorEventJS.kt** — server 事件：包装原生 `SelectGeneratorEvent`，暴露 `candidates`/`force(id)`；桥接回写。kubejs-integration §5。
**compat/kubejs/events/RewardEventJS.kt** — server 事件：包装 `RewardEvent`，暴露 `lootTable`/`extraDrops`。kubejs-integration §5。

> 资源 `kubejs.plugins.txt` 写入 `com.tonywww.blackboard.compat.kubejs.BlackboardKubePlugin`。🟥 KJS7 `EventGroup`/`EventJS` 精确签名落地 1.21 侧时查 KJS7 源码。

---

## 10. 源码：`Blackboard.kt` 入口 + `client/`

**Blackboard.kt** 🟨 — `@Mod("blackboard")` 经 KLF（object 或带 `IEventBus` 构造器；🟨 `@Mod`/`IEventBus` 包名两版不同）。职责：取 mod 总线 → `ModBlocks/ModBlockEntities/ModItems` 各 `register(bus)` → 注册 `Builtin*` → 注册默认渲染器（仅客户端）→ `BlackboardKubePlugin.register(bus)`（若有 KubeJS）→ 安排注册表冻结时机。kotlinlangforge §2、internal §10。

**client/BlackboardClient.kt** 🟥（后续）— 客户端初始化：接入 ApricityUI 时实现 `ApricityBlackboardRenderer : BlackboardRenderer` 并注入 `BlackboardRendering.renderer`。当前为空/占位。设计 §8。

---

## 11. 资源文件

**META-INF/mods.toml** 🟦 — Forge 元数据，`modLoader="klf"`、`[[mods]]`、`[[dependencies.blackboard]]`（forge/minecraft，`mandatory=true`）。multiloader-build §6、kotlinlangforge §1。
**META-INF/neoforge.mods.toml** 🟦 — NeoForge 元数据，`modLoader="klf"`、依赖用 `type="required"`。multiloader-build §6。
**kubejs.plugins.txt** 🟦 — 插件 FQN。kubejs-integration §1。
**pack.mcmeta** 🟦 — 资源/数据包元数据（pack_format 按版本，🟨 数值可用 swap）。
**assets/blackboard/** — blockstates / models（block+item）/ textures / lang（en_us、zh_cn）。
**data/blackboard/loot_tables/rewards/default.json** 🟦 — 默认奖励战利品表（可被数据包覆盖）。设计 §11。

---

## 12. 平台边界清单（🟨 集中点，用 `//? if forge`/`//? if neoforge`）

| 边界 | 文件 | 依据 |
| --- | --- | --- |
| `ResourceLocation` 构造 | api/BlackboardApi.kt | loader-platform §1 |
| 注册句柄 / `RegistryObject` vs `Supplier` | content/Mod*.kt、platform/PlatformRegistration.kt | loader-platform §1 |
| `Properties.copy` vs `ofFullCopy` | content/BlackboardBlock.kt | loader-platform §1 |
| 方块实体同步签名（registries） | content/SyncedBlockEntity.kt、BlackboardBlockEntity.kt | loader-platform §2/§3 |
| `Component` 编解码（🟥C6） | platform/PlatformComponents.kt | loader-platform §6 |
| 服务端聊天事件（🟥C4） | platform/PlatformEvents.kt、chat/ChatHandler.kt | loader-platform §6 |
| 战利品发奖（🟥C5） | platform/PlatformLoot.kt、core/Reward.kt | loader-platform §6 |
| `@Mod`/`IEventBus` 包名 | Blackboard.kt | kotlinlangforge §2/§7 |
| KubeJS 插件基类包名（KJS6/7） | compat/kubejs/BlackboardKubePlugin.kt | kubejs-integration §6 |
| 事件总线触发时机 | platform/PlatformEvents.kt | internal §10 |

---

## 13. 待决策项（design §13）对文件的影响

| §13 决策 | 影响文件 | 默认（未拍板前） |
| --- | --- | --- |
| (1) 作答格式 / boardId 形态 | chat/DefaultAnswerFormat.kt、core/BlackboardManager.kt、api/chat/ParsedAnswer.kt | `!ans <boardId> <答案>`，boardId 待定（自动短 ID / 命名 / 选中） |
| (2) 是否拦截聊天 | chat/ChatHandler.kt、platform/PlatformEvents.kt | 暂不拦截（先放行） |
| (3) ✅ 答对后行为 | core/AnswerHandler.kt、BlackboardBlockEntity.kt | **答对后销毁方块**（`onSolved` 可重写）；每板一题、首杀即解。已移除 `regenerateOnSolved` |
| (4) 难度来源 | api/question/GenerationContext.kt、BlackboardBlockEntity.kt | `difficulty=0` |
| (5) ✅ 多人共享 | BlackboardBlockEntity.kt、core/AnswerHandler.kt | **每板固定一题、全员同一题面**（共享单题） |

> 这些点在落地前先按「默认」实现并以 `🟥/TODO(§13)` 标注；拍板后回改并更新 design/参考。

---

## 14. 实现顺序（里程碑）

1. **M0 工程骨架** ✅（已完成并通过构建）：settings / stonecutter / build.gradle.kts / libs.versions.toml / 节点 gradle.properties / 两份 mods.toml / pack.mcmeta；`Blackboard.kt` 空入口。`:1.20.1-forge:build` 与 `:1.21.1-neoforge:build` 均成功产出已重映射的 mod jar，`mods.toml` 占位符正确展开。依据 multiloader-build、kotlinlangforge。
   - **构建约束**：Stonecutter 0.9.6 的 Gradle 守护进程必须运行在 **Java 21**（已通过 `gradle/gradle-daemon-jvm.properties` 声明 `toolchainVersion=21`）；Forge 节点编译目标 Java 17、NeoForge 节点 Java 21。
2. **M1 核心 API（纯 Kotlin）**：`api/*` 全部 + `core/{QuestionImpl,Contexts,Selection,Reward(去平台部分),AnswerResult}` + `api/registry`、`api/event`。可单元测试（选题/事件/注册/校验）。依据 internal-core-api、answer-format。
3. **M2 方块与同步**：`content/*` + `core/{QuestionNbt,BlackboardManager}` + `platform/{PlatformRegistration,PlatformComponents}`；放置黑板→出题→`getUpdateTag` 同步→No-op 渲染。依据 loader-platform。
4. **M3 作答闭环**：`chat/*` + `platform/PlatformEvents`（聊天）+ `core/AnswerHandler` + `platform/PlatformLoot` + `Reward` 平台部分 + 默认战利品表；聊天作答→判题→发奖。依据 answer-format、loader-platform §6（C4/C5 反编译确认）。
5. **M4 内置内容**：`builtin/*`；若干可玩题型与默认黑板。
6. **M5 KubeJS 兼容**：`compat/kubejs/*` + `kubejs.plugins.txt`（先 1.20.1/KJS6，后 1.21.1/KJS7）。依据 kubejs-integration。
7. **M6 AUI 渲染接入**：`client/BlackboardClient` + `ApricityBlackboardRenderer`；接 ApricityUI（依赖坐标/世界渲染 API 待补，design §8 / 参考 E 项）。

---

## 修订记录
- 2026-06-30：建立主设计大纲；项目结构采用 Architectury Loom（flat）+ Stonecutter；逐文件列出职责、平台边界、§13 影响与实现里程碑。
- 2026-06-30：完成 M0 工程骨架并完成 Gradle 导入；`:1.20.1-forge` 与 `:1.21.1-neoforge` 两节点均能配置并构建出 mod jar。确认 Stonecutter 0.9.6 需 Java 21 守护进程；修复 `processResources` 中需用 `project.property(...)` 限定（任务配置块内隐式接收者为任务）。
