# Blackboard

> 一个高度可扩展的 Minecraft 模组：放置一块「黑板」方块，它会显示一道题目；玩家在聊天里作答，答对即可获得奖励。
> 题库、黑板行为、奖励与选题策略全部通过**注册表 / 事件 / KubeJS** 开放扩展。

- **Mod ID**：`blackboard` ・ **版本**：`0.1.0` ・ **包根**：`com.tonywww.blackboard`
- **跨版本**：单一代码库经 [Stonecutter](https://stonecutter.kikugie.dev/) + Architectury Loom 交叉编译，Kotlin 由 [KotlinLangForge](https://github.com/gaming12846/KotlinLangForge)（`modLoader = "klf"`）提供。

| 目标 | Minecraft | 加载器 | KubeJS |
| --- | --- | --- | --- |
| `1.20.1-forge` | 1.20.1 | Forge 47.4.4 | KubeJS 6（`2001.x`） |
| `1.21.1-neoforge` | 1.21.1 | NeoForge 21.1.234 | KubeJS 7（`2101.x`） |

---

## 目录

- [核心概念](#核心概念)
- [玩法](#玩法)
- [构建](#构建)
- [用 Mod（Java/Kotlin）扩展](#用-modjavakotlin-扩展)
  - [添加新的 QuestionGenerator](#1-添加新的-questiongenerator)
  - [删除现有的 QuestionGenerator](#2-删除现有的-questiongenerator)
  - [注册新的黑板类型](#3-注册新的黑板类型)
  - [使用不同的 QuestionGenerator 库（池）](#4-使用不同的-questiongenerator-库池)
- [用 KubeJS 扩展](#用-kubejs-扩展)
  - [添加新的 QuestionGenerator（JS）](#1-添加新的-questiongeneratorjs)
  - [删除现有的 QuestionGenerator（JS）](#2-删除现有的-questiongeneratorjs)
- [事件总览](#事件总览)
- [现状与限制（路线图）](#现状与限制路线图)
- [文档索引](#文档索引)

---

## 核心概念

| 概念 | 说明 |
| --- | --- |
| **`QuestionGenerator`** | 题目生成器。函数式核心：`generate(ctx) -> Question` 出题，`validate(question, ans) -> AnswerResult` 判题。带 `weight`（权重）与 `tags`（分类标签，用于分池）。 |
| **`BlackboardType`** | 「这块黑板怎么行为」：选题池 `pool`、选题策略 `selector`、答对回调 `onSolved`、奖励表、作答格式、最大作答次数。 |
| **`GeneratorPool`** | 候选生成器来源：`ByTag(tag)` / `Explicit(ids)` / `All`。 |
| **注册表** | `BlackboardRegistries.QUESTION_GENERATORS` 与 `BlackboardRegistries.BLACKBOARD_TYPES`——加载器无关、可在启动期/KubeJS 启动脚本注册，服务器启动前**冻结**为一致快照。 |
| **事件** | `BlackboardEvents.*`（见 [事件总览](#事件总览)），Java/KT 与 KubeJS 均可监听。 |
| **热重载** | `/blackboard reload`（权限 2）在运行期重建生成器注册表（见下）。 |

标签（tag）就是「题库/生成器库」：内置 `#blackboard:math`、`#blackboard:text`、`#blackboard:default`。一个生成器可带多个标签，一个黑板类型可指定从某个标签池选题。

---

## 玩法

1. 在创造物品栏找到 **Blackboard**（黑板）方块并放置。
2. 黑板会显示一道题目。
3. 在聊天中按默认格式作答：

   ```text
   !ans <boardId> <你的答案>
   ```

   - `!ans` 前缀大小写不敏感；`<boardId>` 是该黑板的可寻址标识；答案正文两端会 `trim`，内部空白保留（便于矩阵等答案）。
4. 答对 → 按黑板类型发放奖励（默认战利品表 `blackboard:rewards/default`）。

> ⚠️ 端到端游玩闭环仍在完善中，详见 [现状与限制](#现状与限制路线图)。

---

## 构建

需要 JDK 21（Gradle 守护进程；`1.20.1-forge` 节点运行期用的 JDK 17 会由 Gradle 自动置备）。

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
.\gradlew.bat :1.20.1-forge:build :1.21.1-neoforge:build
```

常用任务：

| 任务 | 作用 |
| --- | --- |
| `:<node>:build` | 编译 + 测试 + 打包该版本节点 |
| `:<node>:runClient` / `:<node>:runServer` | 启动开发环境 |
| `:<node>:runData` | 数据生成（生成方块标签 `#blackboard:blackboards`） |

`<node>` 取 `1.20.1-forge` 或 `1.21.1-neoforge`。Stonecutter 的活动版本可用 IDE 插件或 Stonecutter 任务切换。

---

## 用 Mod（Java/Kotlin）扩展

在模组**构造期**（注册表冻结前）直接写注册表，或监听事件贡献可热重载的内容。下列示例为 Kotlin；Java 用法等价（调用相同的 `builder(...)` 与 `register(...)`）。

> **关于 id**：`BlackboardApi.id("x")` 生成 `blackboard:x`（用于内置命名空间）。你自己的命名空间请构造自己的 `ResourceLocation`——1.21.1 用 `ResourceLocation.fromNamespaceAndPath("mymod", "x")`，1.20.1 用构造函数 `ResourceLocation("mymod", "x")`。下例用一个本地 `myId(...)` 辅助函数表示。

### 1. 添加新的 QuestionGenerator

**方式 A — 启动期直接注册**（成为「启动基线」，最简单）：

```kotlin
import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.question.Questions
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

fun myId(path: String) = ResourceLocation.fromNamespaceAndPath("mymod", path) // 1.20.1: ResourceLocation("mymod", path)

val PARITY: QuestionGenerator =
    QuestionGenerator.builder(myId("parity"))
        // 打上标签，让它进入某些「库」/池（这里加入内置 math 与 default 池）
        .tag(BlackboardTags.MATH, BlackboardTags.DEFAULT)
        .weight(8)
        .generate { ctx ->
            val n = ctx.random.nextIntBetweenInclusive(1, 999)
            Questions.builder(myId("parity"))
                .content(Component.literal("$n 是奇数还是偶数? (odd/even)"))
                .store("answer", if (n % 2 == 0) "even" else "odd")
                .build()
        }
        .validate { q, a ->
            if (a.text.trim().equals(q.getString("answer"), ignoreCase = true)) AnswerResult.correct()
            else AnswerResult.incorrect()
        }
        .build()

// 在模组入口 init（P7-A 冻结之前）调用：
BlackboardRegistries.QUESTION_GENERATORS.register(PARITY)
```

> 判题也可复用内置校验器 `com.tonywww.blackboard.validation.Validators`（`number()` / `text()` / `matrix()` / `textRegex()`），默认从 `Question.data` 的 `"answer"` 键读标准答案。

**方式 B — 通过事件贡献（支持 `/blackboard reload` 热重载）**：

```kotlin
import com.tonywww.blackboard.api.event.BlackboardEvents

// 想让生成器随 /blackboard reload 刷新，应在此事件里注册，而不是在 init 直接写注册表
BlackboardEvents.REGISTER_GENERATORS.register { event ->
    event.register(PARITY)
}
```

两层模型：`init`/KubeJS 启动脚本直接注册者被快照为**启动基线**（每次重载原样恢复）；`REGISTER_GENERATORS` 贡献者属**可重载层**，每次 `/blackboard reload` 重新收集。与基线同 id 的贡献会被忽略并告警（基线优先）。

### 2. 删除现有的 QuestionGenerator

> ⚠️ **暂无「从注册表删除/禁用某个生成器」的 API**（`SimpleRegistry` 只有 `register` / 全量 `reopen` / `freeze`，`RegisterGeneratorsEvent` 只有 `register`）。该能力已登记为待实现任务，见 [现状与限制](#现状与限制路线图)。

当前可用的**临时替代**：在**选题时**把某个生成器从候选中排除（对每次出题生效，但它仍留在注册表里）：

```kotlin
import com.tonywww.blackboard.api.event.BlackboardEvents

BlackboardEvents.SELECT_GENERATOR.register { event ->
    // 例如永远不出内置除法题
    event.candidates.removeIf { it.generator.id == BlackboardApi.id("division") }
}
```

### 3. 注册新的黑板类型

```kotlin
import com.tonywww.blackboard.api.BlackboardApi
import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register
import com.tonywww.blackboard.chat.DefaultAnswerFormat
import com.tonywww.blackboard.core.defaultReward
import com.tonywww.blackboard.core.weightedRandomSelect

val MATH_ONLY: BlackboardType =
    BlackboardType.builder(myId("math_only"))
        .pool(GeneratorPool.ByTag(BlackboardTags.MATH)) // 只从 #blackboard:math 选题
        .selector(::weightedRandomSelect)               // 复用内置加权随机策略
        .onSolved(::defaultReward)                      // 复用内置发奖流程
        .rewardLootTable(BlackboardApi.id("rewards/default"))
        .answerFormat(DefaultAnswerFormat)              // 复用默认 !ans 作答格式
        .maxAttempts(3)                                 // 每题最多 3 次；<=0 表示不限
        .build()

BlackboardRegistries.BLACKBOARD_TYPES.register(MATH_ONLY)
```

`selector` / `onSolved` / `answerFormat` 均可换成你自己的实现（例如自定义作答格式、答对不销毁而是刷新题目等）。

> ⚠️ 类型能被**注册**，但目前还**无法在世界里把某块黑板指定为非默认类型**（放置时不分配类型、且没有触发出题的钩子），因此自定义类型/池暂时无法端到端游玩。此接线缺口见 [现状与限制](#现状与限制路线图)。

### 4. 使用不同的 QuestionGenerator 库（池）

「库」= 一组带同一标签的生成器。用法分两步：

1. **给生成器打标签**（`.tag(...)`），把它归入某个库：

   ```kotlin
   val ALGEBRA_TAG = myId("algebra")

   val QUADRATIC = QuestionGenerator.builder(myId("quadratic"))
       .tag(ALGEBRA_TAG)          // 归入 #mymod:algebra 库
       .weight(5)
       .generate { /* ... */ }
       .validate { /* ... */ }
       .build()
   ```

2. **让黑板类型从该库选题**——`GeneratorPool` 三选一：

   ```kotlin
   GeneratorPool.ByTag(ALGEBRA_TAG)                    // 整个 #mymod:algebra 库
   GeneratorPool.Explicit(listOf(myId("quadratic")))  // 手挑若干 id
   GeneratorPool.All                                   // 全部已注册生成器
   ```

不同黑板类型指向不同的池，即可让不同黑板出自不同题库。

---

## 用 KubeJS 扩展

KubeJS 为**软依赖**：仅当安装了 KubeJS 时集成才启用。事件组名为 `BlackboardEvents`，提供 `registerGenerators`（startup）、`selectGenerator`、`reward`（server）。脚本放在 `kubejs/startup_scripts/` 或 `kubejs/server_scripts/`。

> 版本：1.20.1 用 KubeJS 6、1.21.1 用 KubeJS 7；下述 JS API 在两者一致。

### 1. 添加新的 QuestionGenerator（JS）

在 **startup 脚本**里监听 `registerGenerators` 并 `event.register(generator)`：

```js
// kubejs/startup_scripts/blackboard.js
BlackboardEvents.registerGenerators(event => {
    const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
    const QuestionGenerator = Java.loadClass('com.tonywww.blackboard.api.question.QuestionGenerator')
    const Questions = Java.loadClass('com.tonywww.blackboard.api.question.Questions')
    const AnswerResult = Java.loadClass('com.tonywww.blackboard.api.question.AnswerResult')
    const Component = Java.loadClass('net.minecraft.network.chat.Component')

    // 1.21.1：fromNamespaceAndPath；1.20.1：new ResourceLocation('kubejs', 'triple')
    const id = ResourceLocation.fromNamespaceAndPath('kubejs', 'triple')

    const gen = QuestionGenerator.builder(id)
        .tag(ResourceLocation.fromNamespaceAndPath('blackboard', 'default')) // 进入默认池
        .weight(5)
        .generate(ctx => {
            const n = ctx.random.nextIntBetweenInclusive(1, 50)
            return Questions.builder(id)
                .content(Component.literal(n + ' * 3 = ?'))
                .store('answer', n * 3)
                .build()
        })
        .validate((q, a) => a.text.trim() === ('' + q.getInt('answer'))
            ? AnswerResult.correct()
            : AnswerResult.incorrect())
        .build()

    event.register(gen)
})
```

> KubeJS 启动脚本无法在运行期重跑，故其注册的生成器属**启动基线**，**不随** `/blackboard reload` 刷新（改动需重启）。
>
> 当前从 JS 构造 `QuestionGenerator` 需借助 `Java.loadClass` 直接使用 Java/Kotlin 构建器，较为繁琐；一个更友好的 JS 生成器 DSL 已登记为可选待实现项，见 [现状与限制](#现状与限制路线图)。

你也可以在 **server 脚本**里用 `selectGenerator` 强制某题或按上下文改权重：

```js
// kubejs/server_scripts/blackboard.js
BlackboardEvents.selectGenerator(event => {
    event.force('blackboard:addition')             // 直接指定生成器
    // event.setWeight('blackboard:division', 0)    // 改权重
})
```

### 2. 删除现有的 QuestionGenerator（JS）

> ⚠️ **暂不支持**从 KubeJS 删除/禁用已注册的生成器（`registerGenerators` 事件只有 `register`，没有 `remove`）。该能力已登记为待实现任务，见 [现状与限制](#现状与限制路线图)。

临时替代：在 `selectGenerator`（server 脚本）里按 id 把它从候选中排除（每次出题生效，不改注册表）：

```js
BlackboardEvents.selectGenerator(event => {
    event.removeCandidate('blackboard:division')
})
```

---

## 事件总览

`com.tonywww.blackboard.api.event.BlackboardEvents`（Java/KT）与 KubeJS 事件组 `BlackboardEvents` 一一对应：

| 原生事件 | KubeJS | 时机 | 能做什么 |
| --- | --- | --- | --- |
| `REGISTER_GENERATORS` | `registerGenerators`（startup） | 注册 / `/blackboard reload` | 贡献可重载的生成器 |
| `SELECT_GENERATOR` | `selectGenerator`（server） | 出题前 | 增删候选、改权重、`force` 指定生成器 |
| `QUESTION_GENERATED` | —（暂无桥接） | 题目生成后 | 统计 / 日志 / 二次处理 |
| `ANSWER` | —（暂无桥接） | 判定作答后 | 读取作答结果 |
| `REWARD` | `reward`（server） | 答对发奖前 | 改战利品表 / 追加或取消掉落 |

---

## 现状与限制（路线图）

本节列出与上面用法直接相关、**尚未实现**的能力。它们已作为任务登记在 [`docs/tasks/parallel-tasks.md`](docs/tasks/parallel-tasks.md) 等待实现。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 添加生成器（Mod / KubeJS） | ✅ 已实现 | 启动期注册 + `REGISTER_GENERATORS` 热重载；KubeJS `registerGenerators` |
| 注册黑板类型 / 自定义池（API） | ✅ 已实现 | `BlackboardType.builder` + `GeneratorPool`；注册表与选题逻辑均已就绪并有单测 |
| **删除 / 禁用现有生成器（Mod / KubeJS）** | ❌ 待实现 | 注册表与事件均无删除入口；仅能在 `SELECT_GENERATOR` 里按次排除候选 |
| **黑板类型的世界内接线** | ❌ 待实现 | 放置时未分配 `BlackboardType`、且无触发出题的钩子（`setBlackboardType` / `generateQuestion` 尚无调用点），故自定义类型/池暂无法端到端游玩 |
| 更友好的 KubeJS 生成器 DSL | 🔵 可选 | 目前需 `Java.loadClass` 直接用 Java 构建器 |
| 客户端渲染（题面上屏） | 🔵 可选 | 渲染接口已就绪，默认 No-op；接入 ApricityUI 为可选阶段（P8-A） |

---

## 文档索引

- 设计总览：[`docs/blackboard-design.md`](docs/blackboard-design.md)
- 任务分解 / 进度看板：[`docs/tasks/parallel-tasks.md`](docs/tasks/parallel-tasks.md)、[`docs/tasks/master-outline.md`](docs/tasks/master-outline.md)
- 参考资料（核心 API、作答校验、KubeJS、多加载器构建等）：[`docs/references/`](docs/references/README.md)
