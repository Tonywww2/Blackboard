# 内部参考：核心 API 实装细节

> 范围：本模组**自有**、与加载器/版本无关的核心 API 与参考实现。可直接据此编码。
> 依赖加载器/版本特定 API 的点用 **【平台边界】** 标注，其具体实现见 `README.md` 外部参考 C 段，未拿到资料前以接口占位、不实现。

目录：
1. 命名与基础工具
2. 注册表 `SimpleRegistry`
3. 事件总线 `EventHook`
4. 数据模型 `Question` / `QuestionGenerator` / `BlackboardType`
5. 上下文对象
6. 选题算法（含事件干预）
7. 奖励流程
8. 渲染接口
9. NBT 序列化模式
10. 注册与冻结生命周期
11. 线程与边界约定

---

## 1. 命名与基础工具

- 包根：`com.tonywww.blackboard`
- 对外 API 包：`com.tonywww.blackboard.api.*`
- `MOD_ID = "blackboard"`
- 统一构造 `ResourceLocation` 的辅助：

```kotlin
// com.tonywww.blackboard.api.BlackboardApi
const val MOD_ID = "blackboard"
fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
```

> 【平台边界】`ResourceLocation` 构造在 1.20.1 为 `new ResourceLocation(ns, path)`；1.21.x 为 `ResourceLocation.fromNamespaceAndPath`。统一封装到 `platform`，对外只用 `id(...)` 或传入完整 `ResourceLocation`。

内置标签常量：

```kotlin
object BlackboardTags {
    val DEFAULT = id("default")  // 默认黑板候选池
    val MATH    = id("math")
    val TEXT    = id("text")
}
```

---

## 2. 注册表 `SimpleRegistry`

需求：加载器无关、支持按 `ResourceLocation` 注册/查询、按标签筛选、注册后**冻结**、**确定性迭代顺序**（用于可复现的加权随机）。

```kotlin
package com.tonywww.blackboard.api.registry

import net.minecraft.resources.ResourceLocation

class SimpleRegistry<T : Any>(val name: String) {

    private val entries = LinkedHashMap<ResourceLocation, T>()   // 保序，决定迭代/选题顺序
    private val tagIndex = HashMap<ResourceLocation, LinkedHashSet<ResourceLocation>>()
    private val idOf = HashMap<T, ResourceLocation>()
    @Volatile private var frozen = false
    private val lock = Any()

    /** 注册；重复 id 默认抛错（防止静默覆盖）。 */
    fun register(rid: ResourceLocation, value: T, tags: Set<ResourceLocation> = emptySet()): T {
        synchronized(lock) {
            check(!frozen) { "注册表 '$name' 已冻结，不能再注册: $rid" }
            require(rid !in entries) { "注册表 '$name' 重复 id: $rid" }
            entries[rid] = value
            idOf[value] = rid
            for (t in tags) tagIndex.getOrPut(t) { LinkedHashSet() }.add(rid)
        }
        return value
    }

    fun get(rid: ResourceLocation): T? = entries[rid]
    fun idOf(value: T): ResourceLocation? = idOf[value]
    fun contains(rid: ResourceLocation): Boolean = entries.containsKey(rid)

    /** 保序快照，便于确定性遍历。 */
    fun all(): List<T> = synchronized(lock) { entries.values.toList() }
    fun ids(): List<ResourceLocation> = synchronized(lock) { entries.keys.toList() }

    fun byTag(tag: ResourceLocation): List<T> = synchronized(lock) {
        tagIndex[tag]?.mapNotNull { entries[it] } ?: emptyList()
    }

    fun freeze() { synchronized(lock) { frozen = true } }
    fun isFrozen(): Boolean = frozen
}
```

实现要点：
- 用 `LinkedHashMap` 保证「注册顺序 == 迭代顺序」，配合 `RandomSource` 实现**可复现**的加权随机选题。
- `register` 重复 id 抛错；如确需覆盖（如调试），后续可加 `replace(...)` 显式 API，但默认不允许。
- 当 `T` 自带 `id`（如 `QuestionGenerator.id`）时，提供便捷重载：

```kotlin
fun SimpleRegistry<QuestionGenerator>.register(gen: QuestionGenerator): QuestionGenerator =
    register(gen.id, gen, gen.tags)
```

`BlackboardRegistries`：

```kotlin
object BlackboardRegistries {
    val QUESTION_GENERATORS = SimpleRegistry<QuestionGenerator>("question_generator")
    val BLACKBOARD_TYPES    = SimpleRegistry<BlackboardType>("blackboard_type")

    fun freezeAll() { QUESTION_GENERATORS.freeze(); BLACKBOARD_TYPES.freeze() }
}
```

---

## 3. 事件总线 `EventHook`

需求：跨加载器一致、注册顺序即触发顺序、单个监听器异常不影响其他、对外 API 极简。

```kotlin
package com.tonywww.blackboard.api.event

import org.slf4j.LoggerFactory

class EventHook<T>(private val debugName: String) {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(T) -> Unit>()
    private val log = LoggerFactory.getLogger("Blackboard/$debugName")

    fun register(listener: (T) -> Unit) { listeners += listener }

    fun invoke(event: T) {
        for (l in listeners) {
            try { l(event) }
            catch (e: Throwable) { log.error("事件监听器异常 ($debugName)", e) }
        }
    }
}
```

```kotlin
object BlackboardEvents {
    val SELECT_GENERATOR   = EventHook<SelectGeneratorEvent>("select_generator")
    val QUESTION_GENERATED = EventHook<QuestionGeneratedEvent>("question_generated")
    val ANSWER             = EventHook<AnswerEvent>("answer")
    val REWARD             = EventHook<RewardEvent>("reward")
}
```

约定：所有事件类**携带** `level: ServerLevel`、`pos: BlockPos`、`blockState: BlockState`；`RewardEvent`、`AnswerEvent` 额外带 `player`。事件对象在文档主体（design §6）已定义，此处仅强调字段不可省。

---

## 4. 数据模型

### 4.1 `Question` 与参考实现

```kotlin
package com.tonywww.blackboard.api.question

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

interface Question {
    val generatorId: ResourceLocation
    val content: Component          // 题面（数学题含 LaTeX 文本）
    val data: CompoundTag           // 生成器自定义持久化数据（标准答案等）
    val prompt: Component?          // 可选纯文本题面（聊天/日志）

    fun getInt(key: String): Int
    fun getDouble(key: String): Double
    fun getString(key: String): String
    fun getBoolean(key: String): Boolean
}

internal class QuestionImpl(
    override val generatorId: ResourceLocation,
    override val content: Component,
    override val data: CompoundTag,
    override val prompt: Component?,
) : Question {
    override fun getInt(key: String) = data.getInt(key)
    override fun getDouble(key: String) = data.getDouble(key)
    override fun getString(key: String) = data.getString(key)
    override fun getBoolean(key: String) = data.getBoolean(key)
}
```

Builder（生成器内使用）：

```kotlin
class QuestionBuilder(private val generatorId: ResourceLocation) {
    private var content: Component? = null
    private var prompt: Component? = null
    private val data = CompoundTag()

    fun content(c: Component) = apply { content = c }
    fun prompt(c: Component) = apply { prompt = c }

    fun store(key: String, v: Int)    = apply { data.putInt(key, v) }
    fun store(key: String, v: Double) = apply { data.putDouble(key, v) }
    fun store(key: String, v: String) = apply { data.putString(key, v) }
    fun store(key: String, v: Boolean)= apply { data.putBoolean(key, v) }

    fun build(): Question = QuestionImpl(
        generatorId,
        requireNotNull(content) { "Question.content 未设置: $generatorId" },
        data,
        prompt,
    )
}

// 入口：Question.builder(id)
object Question { fun builder(id: ResourceLocation) = QuestionBuilder(id) }
```

> 注意：`Question` 既是 interface 又需要 `Question.builder(...)` 伴生入口。Kotlin 中 interface 不能直接有伴生 `builder`；实现时把 `builder` 放在**同名 object** 或一个 `Questions` 工厂里。文档示例用 `Question.builder(...)`，落地时统一为 `Questions.builder(...)`（避免与 interface 命名冲突），并在 design 文档同步修正。**（见修订记录）**

### 4.2 `QuestionGenerator`
见 design §5.4。要点：`generate`、`validate` 为不可空 lambda；`weight>=0`；`tags` 用于 `GeneratorPool.ByTag`。

### 4.3 `BlackboardType` 与 `GeneratorPool`

`GeneratorPool.resolve()` 语义：

```kotlin
fun GeneratorPool.resolve(reg: SimpleRegistry<QuestionGenerator> = BlackboardRegistries.QUESTION_GENERATORS): List<QuestionGenerator> =
    when (this) {
        is GeneratorPool.ByTag    -> reg.byTag(tag)
        is GeneratorPool.Explicit -> ids.mapNotNull { reg.get(it) }
        GeneratorPool.All         -> reg.all()
    }
```

默认黑板类型：

```kotlin
val DEFAULT_TYPE = BlackboardType.builder(id("default"))
    .pool(GeneratorPool.ByTag(BlackboardTags.DEFAULT))
    .selector(::weightedRandomSelect)         // 见 §6
    .rewardLootTable(id("rewards/default"))
    .onSolved(::defaultReward)                // 见 §7
    .answerFormat(DefaultAnswerFormat)        // 见 answer-format-and-validation.md
    .maxAttempts(0)                           // 不限次
    .build()
// 答对后销毁方块不在 BlackboardType，而在 BlackboardBlockEntity.onSolved（open，默认 type.onSolved 发奖 + destroyBlock，子类可重写）。
```

---

## 5. 上下文对象

均为只读数据载体，落地用具体类实现 interface（design §5.3 / §6.1）。参考实现：

```kotlin
data class GenerationContextImpl(
    override val level: ServerLevel,
    override val pos: BlockPos,
    override val blockState: BlockState,
    override val blackboard: BlackboardType,
    override val random: RandomSource,
    override val player: ServerPlayer?,
    override val difficulty: Int,
) : GenerationContext

data class AnswerContextImpl(
    override val player: ServerPlayer,
    override val level: ServerLevel,
    override val pos: BlockPos,
    override val blockState: BlockState,
    override val text: String,
) : AnswerContext

data class SelectionContextImpl(
    override val blackboard: BlackboardType,
    override val level: ServerLevel,
    override val pos: BlockPos,
    override val blockState: BlockState,
    override val player: ServerPlayer?,
) : SelectionContext
```

> design §6.1 出现的 `SelectionContext.withCandidates(...)` 仅为示意；落地时**不需要**把候选塞进上下文——选题算法（§6）直接把事件修改后的 `candidates` 传给 `selector` 的实现参数即可。已在 §6 给出最终签名，design 文档相应措辞按此为准。

---

## 6. 选题算法（含事件干预）

最终签名与实现（**这是权威版本**，覆盖 design §6.1 的示意代码）：

```kotlin
/** selector 接收候选列表（已被事件修改）与上下文。 */
typealias Selector = (candidates: List<WeightedGenerator>, ctx: SelectionContext) -> QuestionGenerator

fun selectGenerator(type: BlackboardType, ctx: SelectionContext): QuestionGenerator {
    val candidates = type.pool.resolve()
        .map { WeightedGenerator(it, it.weight) }
        .toMutableList()

    val event = SelectGeneratorEvent(ctx, candidates)
    BlackboardEvents.SELECT_GENERATOR.invoke(event)   // 开发者可增删/改权重/强制指定

    event.forced?.let { return it }
    require(event.candidates.isNotEmpty()) { "黑板 ${type.id} 在 $ctx 处没有候选生成器" }
    return type.selector(event.candidates, ctx)
}

/** 默认 selector：加权随机。权重<=0 视为不可选；全为0时回退等概率。 */
fun weightedRandomSelect(candidates: List<WeightedGenerator>, ctx: SelectionContext): QuestionGenerator {
    val pool = candidates.filter { it.weight > 0 }
    if (pool.isEmpty()) return candidates.random(ctx.level.random.asJavaRandom().let { java.util.Random(it.nextLong()) }.let { kotlin.random.Random(it.nextLong()) }).generator
    val total = pool.sumOf { it.weight }
    var roll = ctx.level.random.nextInt(total)        // 用世界 RandomSource，可复现
    for (wg in pool) { roll -= wg.weight; if (roll < 0) return wg.generator }
    return pool.last().generator
}
```

> 上面回退分支写法可简化；关键是：**用 `ctx.level.random`（`RandomSource`）而非 `Math.random`**，以保证服务端确定性与可测试性。落地时清理回退分支为：`if (pool.isEmpty()) return candidates[ctx.level.random.nextInt(candidates.size)].generator`。

出题完整步骤（方块实体侧，服务端）：
1. `selectGenerator(type, selCtx)` → `gen`
2. `gen.generate(genCtx)` → `question`
3. 广播 `QuestionGeneratedEvent(level,pos,state,question,player)`
4. 持久化 `question`（§9），并标记需要向客户端同步 → 客户端渲染（§8）

---

## 7. 奖励流程

```kotlin
fun defaultReward(rc: RewardContext) {
    // 1) 解析默认战利品表（若有）
    val table = rc.blackboard.rewardLootTable
    val drops = mutableListOf<ItemStack>()
    if (table != null) drops += rollLootTable(rc.level, rc.player, rc.pos, table) // 【平台边界】见外部参考 C5

    // 2) 广播 RewardEvent，允许替换/追加
    val event = RewardEvent(rc.level, rc.pos, rc.blockState, rc.player, rc.question, rc.result,
                            lootTable = table, extraDrops = drops)
    BlackboardEvents.REWARD.invoke(event)

    // 3) 若事件改了 lootTable，则按改后的表重算（约定：extraDrops 始终发放，lootTable 改变时重算其贡献）
    val finalDrops = buildList {
        if (event.lootTable != table && event.lootTable != null)
            addAll(rollLootTable(rc.level, rc.player, rc.pos, event.lootTable!!))   // 【平台边界】
        else if (event.lootTable == table && table != null)
            addAll(drops.subList(0, drops.size))   // 已含原表掉落
        addAll(event.extraDrops.filter { it !in drops }) // 仅追加未计入的
    }

    // 4) 发放（给背包，满则掉落）
    finalDrops.forEach { giveOrDrop(rc.player, it) }     // 【平台边界】
}
```

> 上述「lootTable 改变后重算」的合并规则需在落地时收敛为一条清晰策略，避免重复发放。建议简化为：
> - 默认先 roll `type.rewardLootTable` 填入 `extraDrops`；
> - 事件里把 `lootTable` 当作「**还要不要再 roll**」的开关：事件结束后，如 `lootTable != null` 则 roll 它并入，最终只发 `extraDrops`。
> 落地时以此简化版为准，并回写本文档（见修订记录）。

`RewardContext`（design 未单列，此处补全）：

```kotlin
data class RewardContext(
    val level: ServerLevel,
    val pos: BlockPos,
    val blockState: BlockState,
    val player: ServerPlayer,
    val question: Question,
    val result: AnswerResult.Correct,
    val blackboard: BlackboardType,
)
```

> **答对后销毁方块（design §13(3)）**：`defaultReward` 只负责发奖；「销毁黑板方块」由 `BlackboardBlockEntity.onSolved(player, result)`（`open`）默认执行——先 `type.onSolved(rc)` 发奖，再 `level.destroyBlock(pos, false)`。其他模组的黑板方块**重写 `onSolved`** 即可改变行为（保留方块/重出题）。每块黑板固定一题、全员共享（design §13(5)）。

---

## 8. 渲染接口

见 design §5.2 / §8。落地补充：
- `BlackboardRendering.renderer` 默认 `No-op`。客户端在收到方块实体同步、`content` 变化时调用 `render(...)`。
- 服务端不调用渲染；只负责把 `content`（`Component`）同步到客户端（§9 update tag）。
- AUI 接入时，提供 `ApricityBlackboardRenderer : BlackboardRenderer`，在客户端初始化阶段 `BlackboardRendering.renderer = ApricityBlackboardRenderer`。**当前阶段不实现**（外部参考 E）。

---

## 9. NBT 序列化模式

方块实体存档键（`saveAdditional` / `load`）：

| 键 | 类型 | 含义 |
| --- | --- | --- |
| `BlackboardType` | String | `BlackboardType` 的 `ResourceLocation` |
| `BoardId` | String | 可寻址标识（作答用，design §7；解析策略待 §13 确认）|
| `Attempts` | Int | 当前题目已作答次数 |
| `HasQuestion` | Boolean | 是否存在当前题目 |
| `Question` | Compound | 见下方「Question 子结构」 |

Question 子结构（`Compound`）：

| 键 | 类型 | 含义 |
| --- | --- | --- |
| `Generator` | String | `generatorId` |
| `Content` | String | `Component` 序列化后的字符串 —— **【平台边界】** 编解码 API 见外部参考 C6 |
| `Prompt` | String? | `prompt` 序列化（可空） |
| `Data` | Compound | 生成器自定义 `data` 原样存入 |

参考代码（编解码委托给平台层）：

```kotlin
fun Question.toNbt(): CompoundTag = CompoundTag().apply {
    putString("Generator", generatorId.toString())
    putString("Content", PlatformComponents.serialize(content))   // 【平台边界】
    prompt?.let { putString("Prompt", PlatformComponents.serialize(it)) }
    put("Data", data.copy())
}

fun questionFromNbt(tag: CompoundTag): Question = QuestionImpl(
    generatorId = ResourceLocation.parse(tag.getString("Generator")),
    content = PlatformComponents.deserialize(tag.getString("Content")),   // 【平台边界】
    data = tag.getCompound("Data"),
    prompt = if (tag.contains("Prompt")) PlatformComponents.deserialize(tag.getString("Prompt")) else null,
)
```

> `PlatformComponents.serialize/deserialize` 是平台抽象：1.20.1 用 `Component.Serializer.toJson/fromJson`；1.21.x 用 `ComponentSerialization.CODEC` + `RegistryOps`（需要 `RegistryAccess`/`HolderLookup.Provider`）。**未拿到外部参考 C6 前不实现该抽象**。
> 客户端同步（`getUpdateTag`/`handleUpdateTag`）复用 `Question.toNbt`，但**只需** `Generator`+`Content`（渲染所需），无需 `Data`（答案不应下发客户端，防作弊）。

---

## 10. 注册与冻结生命周期

顺序（由 `platform` 在各加载器的对应时机触发）：
1. **公共初始化**：注册内置 `QuestionGenerator` / `BlackboardType`（`builtin/` 包）。
2. **KubeJS startup**（若存在）：脚本注册自定义生成器/类型（外部参考 D）。
3. **冻结**：在「世界加载前 / 服务器启动完成」时机调用 `BlackboardRegistries.freezeAll()`。冻结后任何 `register` 抛错。
4. **运行期**：选题/出题/作答/发奖只读注册表。

> 各加载器「在哪个事件回调里执行 1/2/3」属【平台边界】，见外部参考 B/C/D。冻结时机需保证晚于 KubeJS startup。

---

## 11. 线程与边界约定

- **题目生成、判题、发奖**只在**服务端主线程**进行（访问 `ServerLevel`/`ServerPlayer`）。
- **渲染**只在**客户端**进行；客户端拿到的 `Question` 不含 `Data`（答案）。
- 注册表/事件总线注册集中在初始化阶段（单线程），运行期只读；`SimpleRegistry` 仍用锁保证安全。
- `EventHook` 监听器内异常被吞并记录，不向上抛，避免单个插件崩溃影响主流程。

---

## 修订记录
- 2026-06-30：建立文档。已标注三处需在落地时回收的措辞：
  1. `Question.builder` 入口将落为 `Questions.builder`（interface 不能带同名伴生）。
  2. 选题 `selector` 最终签名为 `(List<WeightedGenerator>, SelectionContext) -> QuestionGenerator`，废弃 design §6.1 的 `SelectionContext.withCandidates` 示意。
  3. 奖励合并规则采用 §7 末尾「简化版」（lootTable 作为是否再 roll 的开关，最终只发 `extraDrops`）。
