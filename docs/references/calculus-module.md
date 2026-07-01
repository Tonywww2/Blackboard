# 微积分题库模块 · 开发文档（calculus-module）

> 本文档规划「微积分」题库模块的完整实现：从**表达式 AST / LaTeX 生成 / 答案解析**，到**各题型出题逻辑**，再到**装配为 `QuestionGenerator`**。
> 落地哲学（沿用设计文档）：**自写表达式 AST + 符号求导 + 逆向积分 + 数值抽样判题**；核心零依赖（Phase 1），符号判题的解析器亦自研零依赖（Phase 2）。
> 状态：✅ 已实装（M1–M6 全部完成，两节点 `build` 绿，22 道微积分单测通过）。`BlackboardApi.BlackboardTags.CALCULUS`（`blackboard:calculus`）及内置黑板类型 `blackboard:calculus` 已上线。

---

## 0. 目标与范围

- 出题类型：求导、不定积分、定积分、极限（含"某点导数值"这一数值变体）。
- 题面以 **LaTeX** 输出（交给黑板渲染器 P8-A / AUI 上屏），聊天/日志保留**纯文本**兜底。
- 判题走 **数值抽样等价**，不做符号化简比较——因此玩家可用**普通 infix**（`6x+cos(x)`）或 **LaTeX 子集**（`6x+\cos\left(x\right)`）作答，都能判。
- 微积分生成器打 `CALCULUS` 标签；配一个 `ByTag(CALCULUS)` 的内置黑板类型即可上线。

---

## 1. 模块结构：三个类

包：`com.tonywww.blackboard.builtin.calculus`

| 文件 / 类 | 职责（本文档章节） | 依赖 |
| --- | --- | --- |
| **`CalculusExpr.kt`**（LaTeX / 表达式） | 表达式 `Expr` AST + `derivative/simplify/eval`；`toLatex()` / `toInfix()` 渲染；LaTeX 归一化 + infix 文法解析 → 实现 `Validators.ExpressionEvaluator`（见 §2） | 仅 MC `RandomSource`（无 MC 平台类型） |
| **`CalculusProblems.kt`**（出题逻辑） | 各题型模板 → 产出 `CalculusProblem`（题干 Expr/LaTeX + 标准答案 + 安全采样区间 + 答案种类）；纯逻辑（见 §3） | `CalculusExpr` |
| **`CalculusGenerators.kt`**（装配） | `CalculusProblem` → `Question`（`content`=LaTeX、`prompt`=infix、`data` 存答案与判题元数据）；接 `validate`（`Validators.number` / 抽样等价）；打 `CALCULUS` 标签、`ALL` + `register()`；内置 `ByTag(CALCULUS)` 黑板类型（见 §4） | `CalculusExpr` + `CalculusProblems` + `api.*` |

**依赖链单向**：`CalculusExpr ← CalculusProblems ← CalculusGenerators`。前两者不碰 MC 平台/注册表，便于单测。

---

## 2. 类一 `CalculusExpr`：表达式模型 + LaTeX 生成 + 答案解析

### 2.1 AST 节点

```kotlin
sealed interface Expr {
    fun eval(x: Double): Double     // 数值求值（变量固定为 x）
    fun derivative(): Expr          // 符号求导（未化简）
    fun toLatex(): String           // 显示用 LaTeX
    fun toInfix(): String           // 规范 infix（序列化标准答案 / 可被本模块解析）
}
// 具体节点：
//   Const(c) | Var | Neg(e) | Add(a,b) | Sub(a,b) | Mul(a,b) | Div(a,b)
//   Pow(base, n: Const) | Sin(e) | Cos(e) | Exp(e) | Ln(e) | Sqrt(e)
fun Expr.simplify(): Expr           // 0/1 化简、常数折叠、去多余负号
```

### 2.2 三个核心操作

**求导规则**（`derivative()`；`u,v` 为子式）：

| 结构 | 导数 |
| --- | --- |
| `Const` / `Var` | `0` / `1` |
| `u ± v` | `u' ± v'` |
| `u·v` | `u'v + uv'` |
| `u/v` | `(u'v − uv') / v²` |
| `uⁿ`（n 常数） | `n·u^(n−1)·u'` |
| `sin u` / `cos u` | `cos u·u'` / `−sin u·u'` |
| `eᵘ` / `ln u` / `√u` | `eᵘ·u'` / `u'/u` / `u'/(2√u)` |

**化简**（`simplify()`）：`+0/-0`、`*1`、`*0`、`^1`、`^0`、常数折叠、`Neg(Neg)`、合并显然同类；目标只是让 `toLatex/toInfix` 好看，不追求完全规范形（判题不依赖化简）。

**求值**（`eval(x)`）：直接递归；`Ln/Sqrt` 负参、除零由**采样区间**保证不触发（见 §3.5）。

### 2.3 LaTeX 生成 `toLatex()`

精度感知括号：每个节点带一个优先级，子式优先级低于父所需时才包 `\left(\right)`。

| 节点 | LaTeX |
| --- | --- |
| `Div(a,b)` | `\frac{a}{b}` |
| `Pow(x,2)` / `Pow(x,½)` | `x^{2}` / `\sqrt{x}` |
| `Sqrt(e)` | `\sqrt{e}` |
| `Sin/Cos/Ln` | `\sin\left(e\right)`（简单实参可省括号 `\sin x`） |
| `Exp(e)` | `e^{e}` |
| `Mul(2,x)` / `Mul(数,数)` | `2x`（并列） / `2\cdot 3` |
| `Add/Sub/Neg` | `+` / `-` / 前置 `-` |

**题型外壳**（题干整体，由类三/类二拼装）：
`\frac{d}{dx}\left(…\right)` · `\int … \,dx` · `\int_{a}^{b} … \,dx` · `\lim_{x \to a} …`。

### 2.4 规范 infix `toInfix()`

产出可被本模块 `parse()` 再吃回去的纯文本（如 `9*x^2-2`、`-1/2*cos(2*x)`）。用于：① 序列化**标准答案**到 `Question.data`；② 判题时与玩家答案在同一解析器下比较。

### 2.5 答案解析 → `ExpressionEvaluator`

现有钩子（`validation/Validators.kt`）：

```kotlin
fun interface ExpressionEvaluator {
    fun eval(expr: String, vars: Map<String, Double>): Double?  // 失败返回 null
}
```

本类提供实现 `CalculusEvaluator : ExpressionEvaluator`，管线：**玩家原文 → LaTeX 归一化 → infix 解析 → `Expr` → `eval(vars["x"])`**。

**(a) LaTeX 归一化器**（花括号感知的小 tokenizer，非纯正则；只覆盖本模块题目产出的子集）：

```
\frac{A}{B} → (A)/(B)      \sqrt{A} → sqrt(A)      x^{...} → x^(...)
\left \right → 去掉         \cdot → *               \, \! 及空白 → 去掉
\sin \cos \tan \ln \exp \pi → sin cos tan ln exp pi
unicode: √→sqrt  π→pi  ·→*  −(U+2212)→-
```

**(b) infix 解析器**（递归下降 / Pratt，零依赖，产出 `Expr`）：

```
expr   := term (('+'|'-') term)*
term   := factor (('*'|'/'| 隐式乘) factor)*     // 支持 2x、3sin(x)、2(x+1)、(x+1)(x-1)
factor := base ('^' factor)?                     // 右结合
base   := number | 'x' | 'pi' | 'e' | func '(' expr ')' | '(' expr ')' | '-' base
func   := sin|cos|tan|exp|ln|sqrt                 // 大小写不敏感
```

要点：**隐式乘法**（exp4j 等库不支持，故自研）、一元负号、`pi/e` 常量、函数名大小写不敏感。解析失败 → `eval` 返回 `null`（判题据此回 `invalid`）。

---

## 3. 类二 `CalculusProblems`：出题逻辑

### 3.1 产出结构

```kotlin
enum class ProblemType { DIFF, INDEF_INTEGRAL, DEF_INTEGRAL, DERIV_AT_POINT, LIMIT }
enum class AnswerKind { NUMBER, EXPRESSION }

data class CalculusProblem(
    val type: ProblemType,
    val stemLatex: String,          // 题面 LaTeX（含 d/dx、∫、lim 外壳）
    val stemInfix: String,          // 纯文本兜底（chat/日志）
    val answerKind: AnswerKind,
    val answerNumber: Double? = null,   // NUMBER 时
    val answerInfix: String? = null,    // EXPRESSION 时：标准答案的规范 infix
    val sampleLo: Double = 1.0,         // 安全采样区间 [lo, hi]（EXPRESSION 判题用）
    val sampleHi: Double = 3.0,
    val integral: Boolean = false,      // true → 判题用"两点差"消 +C
)
```

### 3.2 判题两大类（决定 `answerKind` 与存什么）

| 类别 | 存储 | 判题 | 需解析玩家输入 |
| --- | --- | --- | --- |
| `NUMBER` | `answerNumber` | `Validators.number`（容差） | 否 |
| `EXPRESSION` | `answerInfix` + 采样区间 | 数值抽样等价 | 是（`CalculusEvaluator`） |

**+C（不定积分）**：积分答案差一个常数，**比两点差** `ans(x₁)−ans(x₂) ≈ std(x₁)−std(x₂)`，常数自动抵消（`integral=true` 时启用）。

### 3.3 各题型生成逻辑

| # | 题型 | `type` | 生成方式 | 标准答案 | `answerKind` |
| --- | --- | --- | --- | --- | --- |
| A | 多项式求导 | DIFF | 随机 `f=Σaᵢxⁱ`（次数 2–4，系数 ±9，去零） | `f'` | EXPRESSION（或 G 数值变体） |
| B | 基本函数求导 | DIFF | 池 `{a·xⁿ, a·sinx, a·cosx, a·eˣ, a·lnx, a·√x}` 选一 | 求导规则 | EXPRESSION |
| C | 和差组合求导 | DIFF | B 池选 2–3 个相加减 | 逐项求导 | EXPRESSION |
| D | 乘积法则 | DIFF | `f=u·v`，`u,v` 取简单因子 | `u'v+uv'` | EXPRESSION |
| E | 商法则 | DIFF | `f=u/v`，`v` 取恒正（`x²+1`/`eˣ`） | `(u'v−uv')/v²` | EXPRESSION |
| F | 链式法则 | DIFF | 外层∈{sin,cos,exp,ln,(·)ⁿ,√}，内层 `ax+b`/`x²+c` | `g'(h)·h'` | EXPRESSION |
| G | 某点导数值 | DERIV_AT_POINT | 用 A–F 造 `f`，求 `f'`，代入随机 `x₀`（保证定义域） | `f'(x₀)` 一个数 | **NUMBER** |
| H | 不定积分 | INDEF_INTEGRAL | **逆向**：先造原函数 `F`，`f=F'.simplify()`，问 `∫f dx` | `F`（+C） | EXPRESSION（`integral=true`） |
| I | 定积分 | DEF_INTEGRAL | 逆向造 `F,f`；选整数界 `a<b`（区间内 `f` 有定义） | `F(b)−F(a)` 一个数 | **NUMBER** |
| J | 极限 | LIMIT | ①连续代入 `→f(a)`；②`0/0` 因式相消 `(x²−a²)/(x−a)→2a`；③`x→∞` 有理式（首系数比 / 0 / ∞） | 一个数（或 ∞） | **NUMBER** |

**逆向积分法**（H/I 核心）：先造答案 `F`、再求导得被积函数 `f`——题目天然可解，绕开"实现积分算法"。

示例：
- A `f=3x³−2x+5` → `9x²−2`
- F `f=sin(2x)` → `2cos(2x)`
- H `F=−½cos(2x)` → `f=sin(2x)`，题面 `∫sin(2x)dx`，答案 `−½cos(2x)+C`
- I `∫₀^π sin x dx = 2`
- J② `lim_{x→3}(x²−9)/(x−3) = 6`

### 3.4 难度分级（`GenerationContext.difficulty` → 题型/参数）

| difficulty | 开放题型 | 判题 |
| --- | --- | --- |
| 0 入门 | A(数值变体)、G、I、J① | 全 NUMBER，免解析 |
| 1 | B、C、J②③ | EXPRESSION/NUMBER |
| 2 | D、E、F | EXPRESSION |
| 3 | H 不定积分 | EXPRESSION(+C) |

系数范围、项数、是否启用乘积/商/链式，均随 difficulty 放宽。

### 3.5 采样与定义域安全（判题正确性关键）

- 每题带 `[sampleLo, sampleHi]`（默认 `[1,3]`），生成时保证该区间内**所有子式有定义**（`ln/√` 参数恒正、分母非零——优先用恒正分母 `x²+1`）。
- `EXPRESSION` 判题在该区间取若干随机点比对，容差 `1e-6`；若玩家表达式在某点无定义（`eval` 返回 `null`）→ 重采样或视作不等价。

---

## 4. 类三 `CalculusGenerators`：装配为 QuestionGenerator

### 4.1 `CalculusProblem` → `Question`

```kotlin
private fun toQuestion(id: ResourceLocation, p: CalculusProblem): Question =
    Questions.builder(id)
        .content(Component.literal(p.stemLatex))   // 交给渲染器（含 LaTeX）
        .prompt(Component.literal(p.stemInfix))     // chat/日志兜底
        .apply {
            when (p.answerKind) {
                AnswerKind.NUMBER     -> store("answer", p.answerNumber!!)         // double
                AnswerKind.EXPRESSION -> {
                    store("answer", p.answerInfix!!)                                // 规范 infix
                    store("lo", p.sampleLo); store("hi", p.sampleHi)
                    store("intg", p.integral)
                }
            }
        }
        .build()
```

**`Question.data` 键约定**：

| 键 | 类型 | 含义 |
| --- | --- | --- |
| `answer` | double / string | NUMBER：数值答案；EXPRESSION：标准答案规范 infix（可多解 `\n` 分隔） |
| `lo` / `hi` | double | 采样区间（EXPRESSION） |
| `intg` | boolean | 是否用两点差消 +C（EXPRESSION） |

> 题面 LaTeX 存于 `content`（渲染器读 `RenderContext.content`）；无需另存 `latex` 键。

### 4.2 判题接线（`validate`）

- **NUMBER 题型**：直接用现成 `Validators.number("answer", tolerance)`（数值容差）。
- **EXPRESSION 题型**：自定义 `validate`，用类一的 `CalculusEvaluator` 抽样等价：
  - 读 `answer`（标准答案 infix）、`lo/hi/intg`；
  - 生成 N 个随机 `x∈[lo,hi]`；
  - `intg=false`：比 `|player(x) − std(x)| ≤ tol`；
  - `intg=true`：比 `|(player(x₁)−player(x₂)) − (std(x₁)−std(x₂))| ≤ tol`；
  - 玩家解析失败 → `AnswerResult.invalid()`（不计次）；能解析但不等价 → `incorrect()`；全通过 → `correct()`。
  - 抽样等价原语（`numericallyEqual` 的加强版：支持两点差）放类一（表达式数学），类三只组合。

> 也可复用 `Validators.expression(...)` 处理**求导**类（`intg=false`），但**不定积分**需两点差，故 EXPRESSION 判题统一走类三的专用 `validate` 更一致。

### 4.3 注册与黑板类型

```kotlin
object CalculusGenerators {
    val ADDITION_DERIV /* … A–J 各一个（或按难度分组）*/: QuestionGenerator = QuestionGenerator.builder(id("calc_diff_poly"))
        .tag(BlackboardTags.CALCULUS)          // 进入 #blackboard:calculus 池
        .weight(…)
        .generate { ctx -> toQuestion(id("calc_diff_poly"), CalculusProblems.differentiation(ctx.random, ctx.difficulty)) }
        .validate { q, a -> judge(q, a) }
        .build()

    val ALL: List<QuestionGenerator> = listOf(/* … */)
    fun register() { ALL.forEach { BlackboardRegistries.QUESTION_GENERATORS.register(it) } }
}
```

配套内置黑板类型（供世界内直接使用）：

```kotlin
val CALCULUS_TYPE = BlackboardType.builder(id("calculus"))
    .pool(GeneratorPool.ByTag(BlackboardTags.CALCULUS))
    .selector(::weightedRandomSelect)
    .onSolved(::defaultReward)
    .rewardLootTable(id("rewards/default"))
    .answerFormat(DefaultAnswerFormat)
    .maxAttempts(0)
    .build()
```

入口 `Blackboard.kt` 在 `BuiltinGenerators.register()` 之后调 `CalculusGenerators.register()`、`BuiltinBlackboardTypes` 侧或此处注册 `CALCULUS_TYPE`（冻结前）。

### 4.4 i18n

题干用翻译键 + LaTeX/表达式作参数，例如
`question.blackboard.diff` = `"求导：%s"`、`question.blackboard.integral` = `"求不定积分：%s"`，`content = Component.translatable(key, latex)`。

---

## 5. 与现有系统的契合点（勿重复造）

- 标签：`BlackboardApi.BlackboardTags.CALCULUS`（已加）。
- 题目：`Questions.builder(id).content/prompt/store(...).build()`；`Question.data` 见 §4.1。
- 判题：`Validators.number`（NUMBER）；`ExpressionEvaluator`（`fun interface`，`eval(expr, vars): Double?`）由类一实现。
- 选题：`GeneratorPool.ByTag(CALCULUS)` + `BlackboardType`。
- 渲染：`content` 存 LaTeX，实际 LaTeX→像素属 **P8-A**（AUI/JLaTeXMath），本模块不负责画；`prompt` 供 chat/日志。
- 随机：`GenerationContext.random`（`RandomSource`，服务端确定性）、`.difficulty`。

---

## 6. 实装阶段

- **Phase 1（零依赖，先能玩）**：`CalculusExpr`（AST + 求导/化简/求值 + `toLatex/toInfix`）+ `CalculusProblems` 的 **G/I/J/A(数值变体)** + `CalculusGenerators` 用 `Validators.number` 判 + `CALCULUS_TYPE`。全程无外部依赖、无解析器。
- **Phase 2（符号答案）**：补 `CalculusExpr` 的 **LaTeX 归一化 + infix 解析器**（`CalculusEvaluator`）；开放 **A–F 求导 + H 不定积分**（抽样等价，H 用两点差）。

---

## 7. 测试计划（两节点均需绿）

- `CalculusExprTest`：求导规则逐条、`simplify` 幂等/正确、`eval` 数值、`toInfix`→`parse`→`eval` 往返、LaTeX 片段快照；解析器：隐式乘法、函数、`pi/e`、非法输入→null；LaTeX 归一化子集。
- `CalculusProblemsTest`：**自洽性**——每题型生成后，`f'` 抽样 == 标准答案（求导）；逆向积分 `F'` 抽样 == 被积函数（积分）；采样区间内所有子式有定义。
- `CalculusGeneratorsTest`：生成器带 `CALCULUS` 标签且可注册；`Question` 结构（content/prompt/data 键）正确；`validate` 三态（正确/错误/无法解析）；数值题型走 `Validators.number`。

---

## 8. 待决 / 未来

- 极限 `∞` 的表示与判题约定（特殊字符串 / 大数阈值）。
- 三角函数判题的周期性：采样区间避开使 `player/std` 恰好在采样点巧合相等的病态情形（多点采样已足够稳健）。
- 玩家 LaTeX 复杂子集（`\dfrac`、隐式 `^` 无花括号等）逐步扩容归一化器。
- 渲染上屏依赖 P8-A（AUI）落地。
- 是否引入符号积分/化简（Symja，LGPL + 重依赖）——当前不引，保持零依赖。

---

## 修订记录
- 2026-07-01：建档。规划微积分模块三类拆分（`CalculusExpr` / `CalculusProblems` / `CalculusGenerators`）、表达式 AST 与 LaTeX 生成/解析、各题型出题逻辑（逆向积分法）、数值抽样判题（含 +C 两点差）、难度分级与两阶段实装。`BlackboardTags.CALCULUS` 已入代码；生成器与黑板类型待实装。
- 2026-07-02：完成全部实装（M1–M6）。M1 `CalculusExpr` AST/求导/化简/渲染；M2 `CalculusProblems` 数值题型 G/I/J（整数答案）；M3 `CalculusGenerators` 数值装配 + 内置类型 + 入口；M4 LaTeX 归一化 + infix 递归下降解析器 + `CalculusEvaluator` + 抽样判等原语（含变量指数 `e^x` → `e^{f·ln a}`）；M5 符号题型 A–F 求导 + H 不定积分（逆向造原函数）；M6 `EXPRESSION` 版 `validate`（抽样等价 + 积分 +C 两点差）。两节点（1.20.1-forge / 1.21.1-neoforge）`build` 全绿，微积分单测共 22 道。i18n 题干键（M3.7/M6.3）延后至 P8-A 渲染阶段。
