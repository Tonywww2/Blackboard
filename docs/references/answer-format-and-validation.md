# 内部参考：作答解析与答案校验

> 范围：聊天作答的解析（`AnswerFormat` / `ParsedAnswer`）与内置校验器 `Validators` 的**文本语法与归一化规则**。均为本模组自有逻辑，与加载器无关。
> 依赖外部库（数值/符号求值）的部分用 **【选型待定】** 标注，对应 `README.md` 外部参考 F，未拍板前只定义接口、不绑定具体库。

目录：
1. `AnswerFormat` / `ParsedAnswer`
2. 默认解析语法
3. 黑板定位（boardId）
4. `Component` 取纯文本
5. 校验器总览
6. 文字题校验
7. 数值题校验
8. 矩阵题校验
9. 表达式/微积分校验（接口先行）
10. 判定与作答机会消耗规则

---

## 1. `AnswerFormat` / `ParsedAnswer`

```kotlin
package com.tonywww.blackboard.api.chat

fun interface AnswerFormat {
    /** 解析一条聊天消息；返回 null 表示「这不是一条作答消息」（放行原聊天）。 */
    fun parse(message: String): ParsedAnswer?
}

data class ParsedAnswer(
    val boardId: String,   // 黑板标识（解析策略见 §3，最终形态待 design §13 确认）
    val answer: String,    // 去除前缀与 boardId 后的答案正文（保留内部空格，仅 trim 两端）
)
```

---

## 2. 默认解析语法 `DefaultAnswerFormat`

当前默认（design §13 待确认，可能调整）：

```
<前缀><空白><boardId><空白><答案...>
```

- 前缀：`!ans`（大小写不敏感）。
- `boardId`：第一个空白分隔的 token（不含空白）。
- 答案正文：`boardId` 之后的**全部剩余文本**，仅两端 `trim`，内部空白原样保留（矩阵/带空格答案需要）。

参考实现：

```kotlin
object DefaultAnswerFormat : AnswerFormat {
    private const val PREFIX = "!ans"
    private val splitter = Regex("\\s+")

    override fun parse(message: String): ParsedAnswer? {
        val msg = message.trimStart()
        if (!msg.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return null
        // 前缀后必须是空白或结束
        val afterPrefix = msg.substring(PREFIX.length)
        if (afterPrefix.isNotEmpty() && !afterPrefix[0].isWhitespace()) return null

        val rest = afterPrefix.trimStart()
        if (rest.isEmpty()) return null
        val firstWs = rest.indexOfFirst { it.isWhitespace() }
        if (firstWs < 0) return null                       // 只有 boardId、没有答案
        val boardId = rest.substring(0, firstWs)
        val answer = rest.substring(firstWs + 1).trim()
        if (boardId.isEmpty() || answer.isEmpty()) return null
        return ParsedAnswer(boardId, answer)
    }
}
```

边界用例（必须通过测试）：
| 输入 | 结果 |
| --- | --- |
| `!ans 1 42` | `("1","42")` |
| `!ANS north [[1,2],[3,4]]` | `("north","[[1,2],[3,4]]")` |
| `  !ans  a   x y ` | `("a","x y")` |
| `!ans 1` | `null`（无答案） |
| `!answer 1 2` | `null`（前缀后非空白，避免误吞 `!answer`）|
| `hello world` | `null` |

> 若 design §13 改为「右键选中黑板，无需写 boardId」，则换用另一个 `AnswerFormat` 实现，`ParsedAnswer.boardId` 置为空并由会话状态提供目标黑板。当前保留可插拔接口。

---

## 3. 黑板定位（boardId → 方块实体）

**【待确认】** 取决于 design §13 第 1 条。三种候选策略，先记录，不实现具体方案：
- (a) 自动短数字 ID：放置时由维度内计数器分配，存入 BE 的 `BoardId`；服务端维护 `Map<dim, Map<boardId, BlockPos>>` 索引（随区块加载/卸载更新）。
- (b) 命名牌/铁砧命名：`BoardId = 自定义名`；同名冲突需处理（拒绝/就近）。
- (c) 选中式：玩家先交互选中黑板，服务端按玩家记录当前目标 BE，消息里不带 boardId。

> 拿到确认后在此补全「索引建立/失效、跨维度、权限校验」细节。

---

## 4. `Component` 取纯文本

判题面向纯文本，需从作答消息（已是字符串）与（如需要）`Question.prompt` 取纯文本：

```kotlin
fun Component.plain(): String = this.string   // MC 提供：扁平化为无格式字符串
```

> 玩家作答来自聊天，本就是 `String`，无需从 `Component` 提取；`prompt`/`content` 仅用于展示与日志。校验只用 `AnswerContext.text`。

---

## 5. 校验器总览

内置便捷校验器，返回 `(Question, AnswerContext) -> AnswerResult`，可直接用于 `QuestionGenerator.validate`：

```kotlin
object Validators {
    fun text(expectedKey: String = "answer", ignoreCase: Boolean = true, trim: Boolean = true): (Question, AnswerContext) -> AnswerResult
    fun textRegex(pattern: Regex): (Question, AnswerContext) -> AnswerResult
    fun number(expectedKey: String = "answer", tolerance: Double = 1e-9): (Question, AnswerContext) -> AnswerResult
    fun matrix(expectedKey: String = "answer", tolerance: Double = 1e-9): (Question, AnswerContext) -> AnswerResult
    fun expression(/* 见 §9 */): (Question, AnswerContext) -> AnswerResult
}
```

约定：标准答案默认存在 `Question.data` 的 `"answer"` 键（类型随题型：String / Double / 矩阵的字符串表示）。

---

## 6. 文字题校验

```kotlin
fun text(expectedKey: String = "answer", ignoreCase: Boolean = true, trim: Boolean = true) =
    { q: Question, a: AnswerContext ->
        var exp = q.getString(expectedKey)
        var got = a.text
        if (trim) { exp = exp.trim(); got = got.trim() }
        val eq = if (ignoreCase) exp.equals(got, ignoreCase = true) else exp == got
        if (eq) AnswerResult.correct() else AnswerResult.incorrect()
    }
```

归一化建议（可选开关，落地时加参数）：折叠连续空白、全角→半角、去除首尾标点。默认仅 `trim` + 大小写不敏感，避免过度宽松。

---

## 7. 数值题校验

- 解析：接受整数、小数、科学计数（`1e3`）、正负号；可选接受分数 `a/b`。
- 比较：`abs(got - exp) <= tolerance`；或相对容差（大数时）。
- 多解：可在 `data` 存多个可接受值（`answer` + `answer2`...）或一个范围。

```kotlin
fun number(expectedKey: String = "answer", tolerance: Double = 1e-9) =
    { q: Question, a: AnswerContext ->
        val exp = q.getDouble(expectedKey)
        val got = parseNumber(a.text)
        when {
            got == null -> AnswerResult.invalid()                       // 无法解析为数 → 不消耗机会
            kotlin.math.abs(got - exp) <= tolerance -> AnswerResult.correct()
            else -> AnswerResult.incorrect()
        }
    }

/** 仅做「字面数值」解析；含变量/函数的表达式求值见 §9【选型待定】。 */
fun parseNumber(s: String): Double? {
    val t = s.trim()
    t.toDoubleOrNull()?.let { return it }
    // 简单分数 a/b
    val m = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*/\\s*([+-]?\\d+(?:\\.\\d+)?)$").find(t) ?: return null
    val d = m.groupValues[2].toDouble()
    return if (d == 0.0) null else m.groupValues[1].toDouble() / d
}
```

> 区分 `incorrect`（解析成功但答错，消耗机会）与 `invalid`（解析失败，不消耗机会）。规则见 §10。

---

## 8. 矩阵题校验

**作答文本语法（玩家输入）**：`[[a, b], [c, d]]`
- 外层 `[` `]` 包裹整体；每行 `[` `]` 包裹；行内元素逗号分隔。
- 元素：数值（同 §7 的 `parseNumber`，含分数）。
- 空白：行/元素间任意空白忽略。
- 也接受分号分隔行的 MATLAB 风格 `[a b; c d]`？→ **默认不接受**，仅支持嵌套方括号形式，保持解析确定性。（如需 MATLAB 风格，落地时加可选解析器并更新本文档。）

**标准答案存储**：`data["answer"]` 存矩阵的规范字符串（如 `[[1,2],[3,4]]`），校验时双方都解析为 `List<List<Double>>` 后逐元素按容差比较；维度不一致 → `incorrect`。

```kotlin
fun matrix(expectedKey: String = "answer", tolerance: Double = 1e-9) =
    { q: Question, a: AnswerContext ->
        val exp = parseMatrix(q.getString(expectedKey))
        val got = parseMatrix(a.text)
        when {
            got == null || exp == null -> if (got == null) AnswerResult.invalid() else AnswerResult.incorrect()
            got.size != exp.size || got.indices.any { got[it].size != exp[it].size } -> AnswerResult.incorrect()
            matricesEqual(exp, got, tolerance) -> AnswerResult.correct()
            else -> AnswerResult.incorrect()
        }
    }

fun parseMatrix(s: String): List<List<Double>>? {
    val t = s.replace("\\s".toRegex(), "")
    if (!t.startsWith("[[") || !t.endsWith("]]")) return null
    val rowsRaw = Regex("\\[([^\\[\\]]*)\\]").findAll(t.substring(1, t.length - 1)).map { it.groupValues[1] }.toList()
    if (rowsRaw.isEmpty()) return null
    val rows = rowsRaw.map { row ->
        if (row.isEmpty()) return null
        row.split(",").map { parseNumber(it) ?: return null }
    }
    return rows
}

fun matricesEqual(x: List<List<Double>>, y: List<List<Double>>, tol: Double): Boolean =
    x.indices.all { i -> x[i].indices.all { j -> kotlin.math.abs(x[i][j] - y[i][j]) <= tol } }
```

测试用例：
| 期望 | 输入 | 结果 |
| --- | --- | --- |
| `[[1,2],[3,4]]` | `[[1, 2], [3, 4]]` | correct |
| `[[1,2],[3,4]]` | `[[1,2],[3,5]]` | incorrect |
| `[[1,2],[3,4]]` | `[[1,2,3],[4,5,6]]` | incorrect（维度不符） |
| `[[1,2],[3,4]]` | `garbage` | invalid |

---

## 9. 表达式/微积分校验（接口先行）

玩家以文本/LaTeX 作答符号题（导数、积分、化简）。等价性判定较难，**默认策略**（design §9）：作者提供若干可接受标准答案 + 可选数值抽样校验。

接口（**不绑定具体库**，【选型待定】见外部参考 F）：

```kotlin
/** 抽象求值器：把表达式字符串在给定变量取值下求数值。具体实现待选型（exp4j/Symja/...）。 */
fun interface ExpressionEvaluator {
    fun eval(expr: String, vars: Map<String, Double>): Double?   // 解析/求值失败返回 null
}

/**
 * 表达式校验：
 *  - acceptedKey: data 中可接受答案的字符串（多个用 \n 分隔），先做归一化字面比较；
 *  - 若提供 evaluator + 抽样点，则对玩家答案与标准答案在若干随机点上比较数值是否一致（容差内）。
 */
fun expression(
    acceptedKey: String = "answer",
    variable: String = "x",
    samples: List<Double> = emptyList(),
    tolerance: Double = 1e-6,
    evaluator: ExpressionEvaluator? = null,
): (Question, AnswerContext) -> AnswerResult = { q, a ->
    val accepted = q.getString(acceptedKey).split('\n').map { normalizeExpr(it) }
    val got = normalizeExpr(a.text)
    when {
        got in accepted -> AnswerResult.correct()
        evaluator != null && samples.isNotEmpty() ->
            if (numericallyEqual(accepted, a.text, variable, samples, tolerance, evaluator))
                AnswerResult.correct() else AnswerResult.incorrect()
        else -> AnswerResult.incorrect()
    }
}

/** 归一化：去空白、统一乘号、小写函数名等（保守，避免误判等价）。具体规则落地时细化并回写文档。 */
fun normalizeExpr(s: String): String = s.replace("\\s".toRegex(), "").replace("·", "*").lowercase()
```

> `numericallyEqual` 在每个抽样点用 `evaluator` 求标准答案与玩家答案的值并比较；任一标准答案在所有抽样点都吻合则判对。**抽样点需避开奇点**（落地时加保护）。
> LaTeX↔可求值表达式 的转换是否需要，取决于「玩家是否用 LaTeX 作答」（外部参考 F3）。未确认前不实现转换。

---

## 10. 判定与作答机会消耗规则

`validate` 返回三态，黑板方块实体据此处理：

| 结果 | 含义 | `Attempts` | 后续 |
| --- | --- | --- | --- |
| `Correct` | 答对 | 不再计 | 触发奖励（§7）；默认**销毁黑板方块**（`BlackboardBlockEntity.onSolved` 可重写）|
| `Incorrect` | 答错 | +1 | 若 `maxAttempts>0` 且达上限 → 锁定/换题（策略待 design §13 确认）|
| `Invalid` | 无法判定（格式错） | 不计 | 回送格式提示，不惩罚 |

> **已确认**：每块黑板固定一题、全员共享同一题面；答对后默认销毁方块（`onSolved` 可重写）。「达到最大作答次数后做什么」仍属 design §13 待确认。

---

## 修订记录
- 2026-06-30：建立文档。待确认依赖：boardId 定位策略（§3）、达上限行为与多人计数（§10）、表达式校验库与 LaTeX 作答（§9，外部参考 F）。
