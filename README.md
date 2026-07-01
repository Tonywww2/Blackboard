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
  - [创建新的黑板方块并绑定类型](#5-创建新的黑板方块并绑定类型)
  - [手动编写题库与 i18n](#6-手动编写题库与-i18n)
- [用 KubeJS 扩展](#用-kubejs-扩展)
  - [添加新的 QuestionGenerator（JS）](#1-添加新的-questiongeneratorjs)
  - [删除现有的 QuestionGenerator（JS）](#2-删除现有的-questiongeneratorjs)
  - [手动编写题库与 i18n（JS）](#3-手动编写题库与-i18njs)
  - [创建新的黑板方块并绑定类型（JS）](#4-创建新的黑板方块并绑定类型js)
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

1. 在创造物品栏找到 **Blackboard**（黑板）方块并放置——放置即自动分配默认黑板类型（`blackboard:default`）并**立即出一道题**。
2. 在聊天中按默认格式作答：

   ```text
   !ans <boardId> <你的答案>
   ```

   - `!ans` 前缀大小写不敏感；`<boardId>` 是该黑板的可寻址标识；答案正文两端会 `trim`，内部空白保留（便于矩阵等答案）。
3. 答对 → 按黑板类型发奖（默认战利品表 `blackboard:rewards/default`）并销毁方块。

**管理指令**（权限等级 2）：

| 指令 | 作用 |
| --- | --- |
| `/blackboard reload` | 热重载题目生成器 |
| `/blackboard settype <typeId>` | 把你正注视的黑板改为指定类型并重新出题（用于在世界内使用自定义类型/题库） |
| `/blackboard generate` | 让你正注视的黑板重新出题 |

> ℹ️ 题面文本已同步到客户端，但**方块上的可视渲染**是可选阶段（P8-A，默认 No-op），详见 [现状与限制](#现状与限制路线图)。

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

在模组**构造期**（注册表冻结前）直接写注册表，或监听事件贡献可热重载的内容。下面每个用法都给出 **Kotlin** 与**完整 Java** 两种写法；各 builder 既示范复用**内置方法**，也示范传入**自定义箭头函数（lambda）**。

> **id 构造**：`BlackboardApi.id("x")` 生成 `blackboard:x`。你自己的命名空间：1.21.1 用 `ResourceLocation.fromNamespaceAndPath("mymod","x")`，1.20.1 用 `new ResourceLocation("mymod","x")`；下例统一用一个 `myId(...)` 辅助表示。

> **Java 互操作提示**（本节 Java 片段已随仓库 `src/test/java/.../JavaApiExampleTest.java` 编译校验）：
> - builder 入口是静态方法：`QuestionGenerator.builder(...)`、`Questions.builder(...)`、`BlackboardType.builder(...)`、`AnswerResult.correct()/incorrect()/invalid()`。
> - 注册表与事件是静态字段：`BlackboardRegistries.QUESTION_GENERATORS`、`BlackboardEvents.REGISTER_GENERATORS`。
> - 单例（`object`）从 Java 用 `.INSTANCE`，如 `DefaultAnswerFormat.INSTANCE`、`Validators.INSTANCE.number(...)`。
> - 顶层/扩展函数编译进 `<文件名>Kt` 类，如 `SelectionKt.weightedRandomSelect(...)`、`RewardKt.defaultReward(...)`。
> - `generate`/`validate`/`selector` 接收 Kotlin 函数类型，Java 直接传 lambda；`onSolved` 返回 `Unit`，Java lambda 末尾要 `return kotlin.Unit.INSTANCE;`。
> - Kotlin 属性在 Java 是 getter：`ctx.getRandom()`、`ans.getText()`、`gen.getId()`、`gen.getTags()`。

### 1. 添加新的 QuestionGenerator

**方式 A — 启动期直接注册**（成为「启动基线」，最简单）。下例出一道奇偶题，`generate`/`validate` 用自定义箭头函数：

<details open><summary><b>Kotlin</b></summary>

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
        .tag(BlackboardTags.MATH, BlackboardTags.DEFAULT) // 归入 math 与 default 池
        .weight(8)
        .generate { ctx ->                                // 自定义 generate 箭头函数
            val n = ctx.random.nextIntBetweenInclusive(1, 999)
            Questions.builder(myId("parity"))
                .content(Component.literal("$n 是奇数还是偶数? (odd/even)"))
                .store("answer", if (n % 2 == 0) "even" else "odd")
                .build()
        }
        .validate { q, a ->                               // 自定义 validate 箭头函数
            if (a.text.trim().equals(q.getString("answer"), ignoreCase = true)) AnswerResult.correct()
            else AnswerResult.incorrect()
        }
        .build()

// 模组入口 init（冻结前）：
BlackboardRegistries.QUESTION_GENERATORS.register(PARITY)
```

</details>

<details><summary><b>Java</b></summary>

```java
import com.tonywww.blackboard.api.BlackboardApi;
import com.tonywww.blackboard.api.question.AnswerResult;
import com.tonywww.blackboard.api.question.QuestionGenerator;
import com.tonywww.blackboard.api.question.Questions;
import com.tonywww.blackboard.api.registry.BlackboardRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

static ResourceLocation myId(String path) {
    return ResourceLocation.fromNamespaceAndPath("mymod", path); // 1.20.1: new ResourceLocation("mymod", path)
}

ResourceLocation id = myId("parity");
QuestionGenerator parity = QuestionGenerator.builder(id)
        .tag(BlackboardApi.BlackboardTags.MATH, BlackboardApi.BlackboardTags.DEFAULT)
        .weight(8)
        .generate(ctx -> {                                 // 自定义 generate 箭头函数
            int n = ctx.getRandom().nextIntBetweenInclusive(1, 999);
            return Questions.builder(id)
                    .content(Component.literal(n + " 是奇数还是偶数? (odd/even)"))
                    .store("answer", n % 2 == 0 ? "even" : "odd")
                    .build();
        })
        .validate((q, a) ->                                // 自定义 validate 箭头函数
                a.getText().trim().equalsIgnoreCase(q.getString("answer"))
                        ? AnswerResult.correct()
                        : AnswerResult.incorrect())
        .build();

// 模组入口（冻结前）：base register(id, value, tags)
BlackboardRegistries.QUESTION_GENERATORS.register(parity.getId(), parity, parity.getTags());
```

</details>

**复用内置校验器**（不写 `validate` 箭头函数）：内置 `Validators` 提供 `number()` / `text()` / `matrix()` / `textRegex()`，默认从 `Question.data` 的 `"answer"` 键读标准答案。

```kotlin
.validate(Validators.number())                        // Kotlin
```
```java
.validate(Validators.INSTANCE.number("answer", 1e-9)) // Java（object 用 INSTANCE，默认参数需给全）
```

**方式 B — 通过事件贡献（支持 `/blackboard reload` 热重载）**：

```kotlin
import com.tonywww.blackboard.api.event.BlackboardEvents

BlackboardEvents.REGISTER_GENERATORS.register { event ->
    event.register(PARITY) // 每次 /blackboard reload 重新贡献
}
```
```java
import com.tonywww.blackboard.api.event.BlackboardEvents;
import kotlin.Unit;

BlackboardEvents.REGISTER_GENERATORS.register(event -> {
    event.register(parity);
    return Unit.INSTANCE; // 监听器是 (T) -> Unit
});
```

两层模型：`init`/KubeJS 启动脚本直接注册者是**启动基线**（每次重载原样恢复）；`REGISTER_GENERATORS` 贡献者是**可重载层**，每次 `/blackboard reload` 重新收集。与基线同 id 的贡献被忽略并告警（基线优先）。

### 2. 删除现有的 QuestionGenerator

在 `REGISTER_GENERATORS` 事件里 `remove(id)`（= `disable(id)`）即可禁用一个已注册的生成器——无论它来自内置、其它 mod 还是 KubeJS 启动脚本；随 `/blackboard reload` 一致生效：

```kotlin
import com.tonywww.blackboard.api.BlackboardApi
import com.tonywww.blackboard.api.event.BlackboardEvents

BlackboardEvents.REGISTER_GENERATORS.register { event ->
    event.remove(BlackboardApi.id("division")) // 禁用内置除法题
}
```
```java
import com.tonywww.blackboard.api.BlackboardApi;
import com.tonywww.blackboard.api.event.BlackboardEvents;
import kotlin.Unit;

BlackboardEvents.REGISTER_GENERATORS.register(event -> {
    event.remove(BlackboardApi.id("division"));
    return Unit.INSTANCE;
});
```

> 若只想**临时**在某次出题排除候选（不动注册表），改在 `SELECT_GENERATOR` 里 `event.candidates.removeIf { ... }`（Kotlin）/ `event.getCandidates().removeIf(...)`（Java）。

### 3. 注册新的黑板类型

`BlackboardType` 决定一块黑板的行为。下面同时给出**复用内置**（`weightedRandomSelect` / `defaultReward`）与**自定义箭头函数**两种写法。

<details open><summary><b>Kotlin</b></summary>

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
import net.minecraft.network.chat.Component

// A) 复用内置 selector / onSolved
val MATH_ONLY = BlackboardType.builder(myId("math_only"))
    .pool(GeneratorPool.ByTag(BlackboardTags.MATH))
    .selector(::weightedRandomSelect)   // 内置加权随机
    .onSolved(::defaultReward)          // 内置发奖
    .rewardLootTable(BlackboardApi.id("rewards/default"))
    .answerFormat(DefaultAnswerFormat)
    .maxAttempts(3)                     // <=0 表示不限次
    .build()

// B) 全部自定义箭头函数：总选第一个候选；答对不销毁只发条消息
val ALWAYS_FIRST = BlackboardType.builder(myId("always_first"))
    .pool(GeneratorPool.All)
    .selector { candidates, _ -> candidates.first().generator }
    .onSolved { rc -> rc.player.sendSystemMessage(Component.literal("答对啦！")) }
    .answerFormat(DefaultAnswerFormat)
    .maxAttempts(0)
    .build()

BlackboardRegistries.BLACKBOARD_TYPES.register(MATH_ONLY)
BlackboardRegistries.BLACKBOARD_TYPES.register(ALWAYS_FIRST)
```

</details>

<details><summary><b>Java</b></summary>

```java
import com.tonywww.blackboard.api.BlackboardApi;
import com.tonywww.blackboard.api.board.BlackboardType;
import com.tonywww.blackboard.api.board.GeneratorPool;
import com.tonywww.blackboard.api.registry.BlackboardRegistries;
import com.tonywww.blackboard.chat.DefaultAnswerFormat;
import com.tonywww.blackboard.core.RewardKt;
import com.tonywww.blackboard.core.SelectionKt;
import kotlin.Unit;
import net.minecraft.network.chat.Component;

// A) 复用内置 selector / onSolved
BlackboardType mathOnly = BlackboardType.builder(myId("math_only"))
        .pool(new GeneratorPool.ByTag(BlackboardApi.BlackboardTags.MATH))
        .selector((candidates, ctx) -> SelectionKt.weightedRandomSelect(candidates, ctx))
        .onSolved(rc -> { RewardKt.defaultReward(rc); return Unit.INSTANCE; })
        .rewardLootTable(BlackboardApi.id("rewards/default"))
        .answerFormat(DefaultAnswerFormat.INSTANCE)
        .maxAttempts(3)
        .build();

// B) 全部自定义箭头函数
BlackboardType alwaysFirst = BlackboardType.builder(myId("always_first"))
        .pool(GeneratorPool.All.INSTANCE)
        .selector((candidates, ctx) -> candidates.get(0).getGenerator())
        .onSolved(rc -> {
            rc.getPlayer().sendSystemMessage(Component.literal("答对啦！"));
            return Unit.INSTANCE;
        })
        .answerFormat(DefaultAnswerFormat.INSTANCE)
        .maxAttempts(0)
        .build();

BlackboardRegistries.BLACKBOARD_TYPES.register(mathOnly.getId(), mathOnly, java.util.Set.of());
BlackboardRegistries.BLACKBOARD_TYPES.register(alwaysFirst.getId(), alwaysFirst, java.util.Set.of());
```

</details>

放置的黑板默认用内置 `blackboard:default` 类型并立即出题；要让某块黑板用你注册的类型，看向它执行 `/blackboard settype mymod:math_only`（见 [玩法](#玩法)）。

### 4. 使用不同的 QuestionGenerator 库（池）

「库」= 一组带同一标签的生成器。用法分两步：**给生成器打标签**归入某个库，再**让黑板类型从该库选题**。

```kotlin
// 1) 打标签：归入 #mymod:algebra 库
val QUADRATIC = QuestionGenerator.builder(myId("quadratic"))
    .tag(myId("algebra"))
    .weight(5)
    .generate { /* ... */ }
    .validate { /* ... */ }
    .build()

// 2) 黑板类型从库里选题——GeneratorPool 三选一
GeneratorPool.ByTag(myId("algebra"))                // 整个 #mymod:algebra 库
GeneratorPool.Explicit(listOf(myId("quadratic")))   // 手挑若干 id
GeneratorPool.All                                   // 全部已注册生成器
```
```java
// Java 侧的三种池
new GeneratorPool.ByTag(myId("algebra"));
new GeneratorPool.Explicit(java.util.List.of(myId("quadratic")));
GeneratorPool.All.INSTANCE;
```

不同黑板类型指向不同的池，即可让不同黑板出自不同题库。

### 5. 创建新的黑板方块并绑定类型

`BlackboardBoards.register(path, typeId)` 注册一个 `blackboard:<path>` 命名空间下的新黑板方块（含 `BlockItem`，自动纳入创造物品栏与方块实体类型有效集）；放置时**默认就是**该类型，无需再 `/blackboard settype`。须在**注册表事件之前**调用（mod 构造期 / KubeJS startup）。

> 前提：`typeId` 指向一个已注册的[黑板类型](#3-注册新的黑板类型)（如前面的 `mymod:math_only`）。方块的 blockstate/模型/贴图/lang 需自备，否则显示为缺失模型。

<details open><summary><b>Kotlin</b></summary>

```kotlin
import com.tonywww.blackboard.content.BlackboardBoards

// 放置即为 mymod:math_only 类型的黑板；方块/物品 id = blackboard:math_board
val MATH_BOARD = BlackboardBoards.register("math_board", myId("math_only"))
```

</details>

<details><summary><b>Java</b></summary>

```java
import com.tonywww.blackboard.content.BlackboardBoards;

var mathBoard = BlackboardBoards.register("math_board", myId("math_only"));
```

</details>

**用自己命名空间的方块**：若你用自己的 `DeferredRegister` 注册了一个 `BlackboardBlock`，用 `bind(...)` 把它绑定到类型并纳入方块实体类型有效集：

```kotlin
BlackboardBoards.bind(MY_BOARD, myId("math_board"), myId("math_only")) // Kotlin
```
```java
BlackboardBoards.bind(myBoard, myId("math_board"), myId("math_only")); // Java
```

### 6. 手动编写题库与 i18n

题面 `content` 是一个 `Component`，因此可用 `Component.translatable("键", 参数...)` 让文本按玩家语言本地化。下例把一组「Minecraft 原版知识」题写进一个**手动题库**生成器（`generate` 从固定列表随机取一条）：

<details open><summary><b>Kotlin</b></summary>

```kotlin
// 一条题：翻译键 + 标准答案
private data class VanillaQ(val key: String, val answer: String)

private val VANILLA_BANK = listOf(
    VanillaQ("question.mymod.diamond_tool", "pickaxe"),   // 挖钻石用什么工具
    VanillaQ("question.mymod.creeper_weakness", "cat"),   // 苦力怕怕什么
    VanillaQ("question.mymod.portal_block", "obsidian"),  // 下界传送门框架方块
)

val VANILLA_KNOWLEDGE = QuestionGenerator.builder(myId("vanilla_knowledge"))
    .tag(myId("vanilla"), BlackboardTags.DEFAULT)
    .weight(5)
    .generate { ctx ->
        val q = VANILLA_BANK[ctx.random.nextInt(VANILLA_BANK.size)]
        Questions.builder(myId("vanilla_knowledge"))
            .content(Component.translatable(q.key)) // i18n：题面按语言显示
            .store("answer", q.answer)
            .build()
    }
    .validate { question, ans ->
        if (ans.text.trim().equals(question.getString("answer"), ignoreCase = true)) AnswerResult.correct()
        else AnswerResult.incorrect()
    }
    .build()
```

</details>

<details><summary><b>Java</b></summary>

```java
import java.util.List;

record VanillaQ(String key, String answer) {}

static final List<VanillaQ> VANILLA_BANK = List.of(
        new VanillaQ("question.mymod.diamond_tool", "pickaxe"),
        new VanillaQ("question.mymod.creeper_weakness", "cat"),
        new VanillaQ("question.mymod.portal_block", "obsidian"));

ResourceLocation vid = myId("vanilla_knowledge");
QuestionGenerator vanilla = QuestionGenerator.builder(vid)
        .tag(myId("vanilla"), BlackboardApi.BlackboardTags.DEFAULT)
        .weight(5)
        .generate(ctx -> {
            VanillaQ q = VANILLA_BANK.get(ctx.getRandom().nextInt(VANILLA_BANK.size()));
            return Questions.builder(vid)
                    .content(Component.translatable(q.key())) // i18n
                    .store("answer", q.answer())
                    .build();
        })
        .validate((question, ans) ->
                ans.getText().trim().equalsIgnoreCase(question.getString("answer"))
                        ? AnswerResult.correct()
                        : AnswerResult.incorrect())
        .build();
```

</details>

对应的语言文件（`src/main/resources/assets/mymod/lang/*.json`）：

```jsonc
// zh_cn.json
{
  "question.mymod.diamond_tool": "挖钻石矿最快用什么工具？(pickaxe/shovel/axe)",
  "question.mymod.creeper_weakness": "苦力怕害怕哪种生物？(cat/wolf/iron_golem)",
  "question.mymod.portal_block": "下界传送门的框架用什么方块？"
}
```
```jsonc
// en_us.json
{
  "question.mymod.diamond_tool": "Fastest tool to mine diamond ore? (pickaxe/shovel/axe)",
  "question.mymod.creeper_weakness": "Which mob scares a creeper? (cat/wolf/iron_golem)",
  "question.mymod.portal_block": "Which block frames a Nether portal?"
}
```

**带占位符的 i18n 格式化**：`translatable` 可传参数，配合 `%n$s` 占位符做模板填充——例如把算式塞进本地化模板（键 `question.mymod.add` 的值为 `"%1$s + %2$s = ?"`）：

```kotlin
.content(Component.translatable("question.mymod.add", a.toString(), b.toString())) // Kotlin
```
```java
.content(Component.translatable("question.mymod.add", String.valueOf(a), String.valueOf(b))); // Java
```

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

在 startup 脚本的 `registerGenerators` 事件里调用 `event.remove(id)`，即可禁用某个已注册的生成器（内置或其它 mod 的都行）。它在启动基线快照前从注册表移除，故也**不随** `/blackboard reload` 复活：

```js
// kubejs/startup_scripts/blackboard.js
BlackboardEvents.registerGenerators(event => {
    const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
    // 1.21.1：fromNamespaceAndPath；1.20.1：new ResourceLocation('blackboard', 'division')
    event.remove(ResourceLocation.fromNamespaceAndPath('blackboard', 'division'))
})
```

> 若只想临时在某次出题排除候选（不改注册表），用 server 脚本的 `selectGenerator`：
>
> ```js
> BlackboardEvents.selectGenerator(event => event.removeCandidate('blackboard:division'))
> ```

### 3. 手动编写题库与 i18n（JS）

和 [Mod 版](#6-手动编写题库与-i18n) 一样，`content` 用 `Component.translatable(键)` 本地化；下例在 startup 脚本里注册一个「Minecraft 原版知识」手动题库：

```js
// kubejs/startup_scripts/blackboard.js
BlackboardEvents.registerGenerators(event => {
    const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
    const QuestionGenerator = Java.loadClass('com.tonywww.blackboard.api.question.QuestionGenerator')
    const Questions = Java.loadClass('com.tonywww.blackboard.api.question.Questions')
    const AnswerResult = Java.loadClass('com.tonywww.blackboard.api.question.AnswerResult')
    const Component = Java.loadClass('net.minecraft.network.chat.Component')

    const bank = [
        ['question.kubejs.diamond_tool', 'pickaxe'],
        ['question.kubejs.creeper_weakness', 'cat'],
        ['question.kubejs.portal_block', 'obsidian'],
    ]
    const id = ResourceLocation.fromNamespaceAndPath('kubejs', 'vanilla_knowledge')

    event.register(QuestionGenerator.builder(id)
        .tag(ResourceLocation.fromNamespaceAndPath('blackboard', 'default'))
        .weight(5)
        .generate(ctx => {
            const q = bank[ctx.random.nextInt(bank.length)]
            return Questions.builder(id)
                .content(Component.translatable(q[0])) // i18n
                .store('answer', q[1])
                .build()
        })
        .validate((question, ans) => ans.text.trim().toLowerCase() === question.getString('answer')
            ? AnswerResult.correct()
            : AnswerResult.incorrect())
        .build())
})
```

语言文件放到 `kubejs/assets/kubejs/lang/*.json`（键与上面一致），例如 `zh_cn.json`：

```json
{
  "question.kubejs.diamond_tool": "挖钻石矿最快用什么工具？(pickaxe/shovel/axe)",
  "question.kubejs.creeper_weakness": "苦力怕害怕哪种生物？(cat/wolf/iron_golem)",
  "question.kubejs.portal_block": "下界传送门的框架用什么方块？"
}
```

### 4. 创建新的黑板方块并绑定类型（JS）

KubeJS **startup 脚本**运行在注册表结算之前，因此可直接调用 `BlackboardBoards.register(path, typeId)` 注册一个新黑板方块（`blackboard:<path>`）并绑定到某个**已存在**的黑板类型（内置 `blackboard:default`，或某个 mod 注册的类型）：

```js
// kubejs/startup_scripts/blackboard.js（必须是 startup 脚本）
const BlackboardBoards = Java.loadClass('com.tonywww.blackboard.content.BlackboardBoards')

// blackboard:quiz_board，放置即为 blackboard:default 类型
BlackboardBoards.register('quiz_board', 'blackboard:default')
```

> - 方块/物品 id 固定在 `blackboard:` 命名空间下（path 由你给）。
> - 需自备该方块的 blockstate/模型/贴图（放资源包或 `kubejs/assets/blackboard/...`），否则显示为缺失模型。
> - KubeJS 不能注册新的黑板**类型**（类型仍由 mod 提供）；这里绑定的是已存在的类型。

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

本节汇总各能力的实现状态。仍为「🔵 可选」的项已作为可选任务登记在 [`docs/tasks/parallel-tasks.md`](docs/tasks/parallel-tasks.md)。

| 能力 | 状态 | 说明 |
| --- | --- | --- |
| 添加生成器（Mod / KubeJS） | ✅ 已实现 | 启动期注册 + `REGISTER_GENERATORS` 热重载；KubeJS `registerGenerators` |
| 删除 / 禁用现有生成器（Mod / KubeJS） | ✅ 已实现 | `REGISTER_GENERATORS` 的 `event.remove(id)`；KubeJS `registerGenerators` 的 `event.remove(id)`；随 `/blackboard reload` 一致生效 |
| 注册黑板类型 / 自定义池 | ✅ 已实现 | `BlackboardType.builder` + `GeneratorPool`；有单测 |
| 黑板类型的世界内使用 | ✅ 已实现 | 放置自动给默认类型并出题；`/blackboard settype`/`generate` 指定/重出题 |
| 创建新黑板方块并绑定类型（Mod / KubeJS） | ✅ 已实现 | `BlackboardBoards.register(path, typeId)` / `bind(...)`；KubeJS startup 脚本亦可调用 |
| 题库为空不崩溃 | ✅ 已实现 | 移除全部生成器时优雅跳过出题（`selectGenerator` 空池返回 `null`，答题回提示） |
| 更友好的 KubeJS 生成器 DSL | 🔵 可选 | 目前需 `Java.loadClass` 直接用 Java 构建器 |
| 客户端渲染（题面上屏） | 🔵 可选 | 渲染接口已就绪，默认 No-op；接入 ApricityUI 为可选阶段（P8-A） |

---

## 文档索引

- 设计总览：[`docs/blackboard-design.md`](docs/blackboard-design.md)
- 任务分解 / 进度看板：[`docs/tasks/parallel-tasks.md`](docs/tasks/parallel-tasks.md)、[`docs/tasks/master-outline.md`](docs/tasks/master-outline.md)
- 参考资料（核心 API、作答校验、KubeJS、多加载器构建等）：[`docs/references/`](docs/references/README.md)
