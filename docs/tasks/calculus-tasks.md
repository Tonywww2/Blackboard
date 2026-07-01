# 微积分模块 · 实装任务清单（calculus-tasks）

> 依据设计文档 [../references/calculus-module.md](../references/calculus-module.md)。三类拆分：`CalculusExpr`（LaTeX/表达式）·`CalculusProblems`（出题逻辑）·`CalculusGenerators`（装配）。
> **门禁规则**：每个模块（M#）完成后，先跑该模块单测（M#T）通过，再 `:1.20.1-forge:build :1.21.1-neoforge:build` **两节点绿**，方可进入下一模块。
> 包：`com.tonywww.blackboard.builtin.calculus`；测试包：`src/test/kotlin/com/tonywww/blackboard/builtin/calculus`。
> 图例：`[ ]` 未做 · `[~]` 进行中 · `[x]` 完成。

---

## 约定

- **零依赖**：Phase 1 与 Phase 2 均不引入外部库（解析器/求值器自研）。
- **纯逻辑优先**：`CalculusExpr`/`CalculusProblems` 不引用 MC 平台/注册表类型（仅用 `net.minecraft.util.RandomSource`），可纯单测；`CalculusGenerators` 才接 `api.*`。
- **确定性**：出题只用 `GenerationContext.random`（`RandomSource`），单测传固定种子。
- 每步列 **产出** 与 **验收**；每模块末列 **单测用例**。

---

# 阶段一：零依赖数值题型（先能玩，判题走 `Validators.number`）

## M1 · `CalculusExpr`：AST + 求导/化简/求值 + 渲染

- [ ] **M1.1 AST 节点**：`sealed interface Expr` + `Const/Var/Neg/Add/Sub/Mul/Div/Pow(base,nConst)/Sin/Cos/Exp/Ln/Sqrt`。
  - 验收：编译通过；节点不可变（`data class`/`val`）。
- [ ] **M1.2 `eval(x: Double): Double`**：递归数值求值。
- [ ] **M1.3 `derivative(): Expr`**：按设计 §2.2 规则表（和/差/积/商/幂/链式/sin/cos/exp/ln/sqrt）。
- [ ] **M1.4 `Expr.simplify(): Expr`**：`+0`/`*1`/`*0`/`^1`/`^0`/常数折叠/`Neg(Neg)`；幂等。
- [ ] **M1.5 `toInfix(): String`**：规范 infix（可被 M4 解析器吃回），带最小必要括号。
- [ ] **M1.6 `toLatex(): String`**：优先级感知括号 + 函数/分式/根式/幂（设计 §2.3）。
- [ ] **M1.7** 题型外壳工具：`diffLatex(e)`=`\frac{d}{dx}(…)`、`indefIntLatex(e)`、`defIntLatex(e,a,b)`、`limitLatex(e,a)`；对应 infix 兜底串。

**M1T · `CalculusExprTest`**
- [ ] eval：各节点在已知点取值正确（`Sin(π/2)=1`、`Ln(e)=1`、`Sqrt(4)=2`、`Pow(x,3)@2=8` …）。
- [ ] derivative（逐规则，用**有限差分抽样**或手算期望核对）：`x^3→3x^2`、`sin→cos`、`cos→-sin`、`e^x→e^x`、`ln→1/x`、`sqrt→1/(2√x)`、积法则、商法则、链式 `sin(2x)→2cos(2x)`。
- [ ] simplify：`x+0/ x*1 / x*0 / x^1 / x^0 / --x` 化简正确；`simplify(simplify(e))==simplify(e)`（幂等）。
- [ ] toLatex：`Div/Pow/Sqrt/Sin` 片段快照（字符串断言）。
- [ ] toInfix：几个表达式的字符串断言（M4 落地后再加往返测试）。

---

## M2 · `CalculusProblems`：数值题型（G 某点导数值 / I 定积分 / J 极限）

- [x] **M2.1** `CalculusProblem` data + `ProblemType`/`AnswerKind` 枚举（设计 §3.1）。
- [x] **M2.2** 私有模板生成器（**定义域安全**，默认区间 `[1,3]`）：`randomPolynomial(r,deg)`、`randomBasic(r)`、`randomAntiderivative(r)`（用于逆向积分）、`randomLinearInner(r)`。
- [x] **M2.3** `derivativeAtPoint(r, difficulty)`（G）：造 `f`→`f'=f.derivative().simplify()`→取安全 `x₀`→`answerNumber=f'.eval(x₀)`；`AnswerKind.NUMBER`。
- [x] **M2.4** `definiteIntegral(r, difficulty)`（I）：**逆向**造 `F`→`f=F'`；取整数界 `a<b`（区间内 `f` 有定义）→`answerNumber=F.eval(b)-F.eval(a)`。
- [x] **M2.5** `limit(r, difficulty)`（J）：模板①连续代入 `f(a)`；②`0/0` 因式相消 `(x²−a²)/(x−a)→2a`；③`x→∞` 有理式（首系数比 / 0 / ∞ 占位）。
- [x] **M2.6** `stemLatex/stemInfix` 用 M1.7 外壳拼装。

**M2T · `CalculusProblemsTest`**（固定种子，遍历多种子；两节点 5/5）
- [ ] 确定性：同种子多次生成结果一致。
- [ ] G 自洽：`answerNumber ≈ f'(x₀)`（用有限差分独立核算，容差 1e-4）。
- [ ] I 自洽：`answerNumber ≈` 对 `f` 的**数值积分**（Simpson，容差 1e-3）。
- [ ] J：三类模板答案等于已知闭式（如 ②给定 `a` 得 `2a`）。
- [ ] 定义域安全：多种子下，`stem` 对应的 `f` 在 `[lo,hi]` 全程有限（无 NaN/Inf）。

---

## M3 · `CalculusGenerators`：数值装配 + 黑板类型 + 入口 + i18n

- [x] **M3.1** `toQuestion(id, p)`：`content=Component.literal(p.stemLatex)`、`prompt=Component.literal(p.stemInfix)`；`NUMBER` 存 `store("answer", answerNumber)`（double）。
- [x] **M3.2** 数值 `validate`：直接用 `Validators.number("answer", tol)`。
- [x] **M3.3** 为 G/I/J 各建 `QuestionGenerator`：`.tag(BlackboardTags.CALCULUS)` + 权重 + `generate{...}` + `validate`。
- [x] **M3.4** `ALL` + `register()`（写入 `BlackboardRegistries.QUESTION_GENERATORS`）。
- [x] **M3.5** 内置黑板类型 `CALCULUS_TYPE`（`GeneratorPool.ByTag(CALCULUS)` + `weightedRandomSelect` + `defaultReward` + `DefaultAnswerFormat` + `rewards/default`）+ 其 `register()`。
- [x] **M3.6** 入口接线：`Blackboard.kt` 在 `BuiltinGenerators.register()` 后调 `CalculusGenerators.register()`；在 `BuiltinBlackboardTypes.register()` 处或此处注册 `CALCULUS_TYPE`（**冻结前**）。
- [~] **M3.7** i18n（暂缓：阶段一题面直接用 `content`=LaTeX / `prompt`=infix；待 P8-A 渲染时统一包翻译键）。

**M3T · `CalculusGeneratorsTest`**
- [ ] 每个生成器含 `CALCULUS` 标签，且可注册进新建 `SimpleRegistry`。
- [ ] `generate` 产出 `Question`：`content`/`prompt` 非空、`data` 含 `answer`（double）。
- [ ] `validate` 三态：正确数值→`Correct`；错误数值→`Incorrect`；非数值文本→`Invalid`。
- [ ] `CALCULUS_TYPE` 注册后，`GeneratorPool.ByTag(CALCULUS).resolve()` 能解析到这些生成器。

> **门禁 G1**：M1T+M2T+M3T 通过 → 两节点 `build` 绿。至此**数值微积分题可端到端游玩**（放置黑板/`settype blackboard:calculus`→出题→`!ans` 数值作答）。—— ✅ **已达成**（阶段一完成，15 项 calculus 单测两节点通过；题面可视渲染依 P8-A）。

---

# 阶段二：符号答案 + 解析器（判题走数值抽样等价）

## M4 · `CalculusExpr` 解析扩展：LaTeX 归一化 + infix 解析 + `ExpressionEvaluator`

- [x] **M4.1** infix 分词 + 递归下降/Pratt 解析 `parse(s): Expr?`：支持 `+ - * / ^`、**隐式乘法**（`2x`/`3sin(x)`/`(x+1)(x-1)`）、一元负、函数 `sin cos tan exp ln sqrt`、常量 `pi e`、变量 `x`；非法→`null`。
- [x] **M4.2** `normalizeLatex(s): String`（花括号感知）：`\frac{}{}`→`()/()`、`\sqrt{}`→`sqrt()`、`x^{}`→`x^()`、去 `\left\right \, \!`、`\cdot`→`*`、`\sin/\cos/\ln/\exp/\pi`、unicode `√ π · −`。
- [x] **M4.3** `object CalculusEvaluator : ExpressionEvaluator`：`eval(expr, vars) = parse(normalizeLatex(expr))?.eval(vars["x"])`。
- [x] **M4.4** 判题原语：`sampleEqual(std, got, lo, hi, n, tol)`（逐点）与 `sampleEqualUpToConst(std, got, …)`（两点差消 +C）。

**M4T · `CalculusParserTest`**
- [x] 解析+求值：`2x+1`、`3sin(x)`、`(x+1)(x-1)`、`-x^2`、`1/2*x^2`、`sqrt(x)`、`exp(x)`/`e^x`、`pi`。
- [x] 优先级/结合：`2+3*4=14`、`2^3^2=512`（右结合）、`-2^2=-4`。
- [x] 隐式乘法：`2x`、`2(x+1)`、`(x+1)(x-1)`。
- [x] LaTeX 归一化：`\frac{1}{2}x^2` 求值 `=0.5·x²`、`2\cos\left(x\right)`、`\sqrt{x}`、unicode `√(x)`/`π`。
- [x] 往返：随机 `Expr`，`parse(e.toInfix())!!.eval(x) ≈ e.eval(x)`。
- [x] 健壮性：`2x+`、`sin(`、空串 → `eval` 返回 `null`。

---

## M5 · `CalculusProblems` 符号题型（A–F 求导 + H 不定积分）

- [x] **M5.1** `differentiation(r, difficulty)`：按难度选 A 多项式 / B 基本 / C 和差 / D 积 / E 商（恒正分母）/ F 链式；`answerKind=EXPRESSION`，`answerInfix = f.derivative().simplify().toInfix()`。
- [x] **M5.2** `indefiniteIntegral(r, difficulty)`（H）：逆向造 `F`→`f=F'`；`answerInfix=F.toInfix()`；`integral=true`。
- [x] **M5.3** 难度门控（设计 §3.4）：0→G/I/J；1→B/C；2→D/E/F；3→H。
- [x] **M5.4** 采样区间随题写入 `sampleLo/sampleHi`。

**M5T · `CalculusSymbolicTest`**
- [x] 求导自洽：生成 `(f, answerInfix)`，`sampleEqual(answerInfix, f.derivative().toInfix(), …)` 为真。
- [x] 积分自洽：H 生成 `(f 被积, F answerInfix)`，`parse(F).derivative()` 抽样 == 被积函数 `f`。
- [x] +C：`F.toInfix()` 与 `F.toInfix()+"+5"` 在 `sampleEqualUpToConst` 下都判等价。
- [x] 定义域安全：多种子下 `f/F` 在 `[lo,hi]` 有限。

---

## M6 · `CalculusGenerators` 符号装配

- [x] **M6.1** `EXPRESSION` 版 `validate`：读 `answer`(infix)/`lo`/`hi`/`intg`→取 N 随机点→`intg?sampleEqualUpToConst:sampleEqual`；玩家 `parse` 失败→`Invalid`；不等价→`Incorrect`；通过→`Correct`。
- [x] **M6.2** 为 A–F、H 建 `QuestionGenerator`（tag `CALCULUS`）：`DIFFERENTIATION`（A–F 按难度）/`INDEF_INTEGRAL`（H），并入 `ALL`。
- [~] **M6.3** i18n：求导/不定积分题干键——**延后**（与 M3.7 一致，现阶段题干直接用 LaTeX/infix，i18n 统一到 P8-A 渲染阶段处理）。

**M6T · `CalculusSymbolicGenTest`**
- [x] EXPRESSION `validate` 三态：同解不同形被接受（LaTeX `\cdot` 等价写法）；积分 `+C` 被接受；非常数差→`Incorrect`；乱输入→`Invalid`。
- [x] `Question.data` 键齐全（`answer`/`lo`/`hi`/`intg`）。

> **门禁 G2**：M4T+M5T+M6T 通过 → 两节点 `build` 绿。✅ **已达成**（两节点 `build` 绿，共 22 道微积分单测）——**符号微积分题**（求导/不定积分）可作答判定。

---

# 收尾

- [x] **F1** 更新 [../references/calculus-module.md](../references/calculus-module.md) 状态（📝→✅ 已实装）与修订记录。
- [x] **F2** 在 [parallel-tasks.md](parallel-tasks.md) 进度看板加一行「微积分题库模块 ✅」，并追加修订记录。
- [~] **F3**（可选）README 加一个「微积分生成器」Mod 示例（复用 `CALCULUS` 池）——**暂缓**（内置类型 `blackboard:calculus` 已可直接用；待 P8-A 渲染就绪后连同 i18n 一并补 README）。
- [x] **F4** 全量回归：`:1.20.1-forge:build :1.21.1-neoforge:build` 两节点绿、全部单测通过（`clean` 后共 77 道 / 0 失败 / 0 错误）。

---

## 本模块进度看板

| 模块 | 内容 | 单测 | 状态 |
| --- | --- | --- | --- |
| M1 | `CalculusExpr` AST/求导/化简/渲染 | `CalculusExprTest` | [x] |
| M2 | `CalculusProblems` 数值题型 G/I/J | `CalculusProblemsTest` | [x] |
| M3 | `CalculusGenerators` 数值装配+类型+入口 | `CalculusGeneratorsTest` | [x] |
| M4 | `CalculusExpr` 解析器+`Evaluator` | `CalculusParserTest` | [x] |
| M5 | `CalculusProblems` 符号题型 A–F/H | `CalculusSymbolicTest` | [x] |
| M6 | `CalculusGenerators` 符号装配 | `CalculusSymbolicGenTest` | [x] |

---

## 修订记录
- 2026-07-01：建立微积分模块实装任务清单；两阶段六模块（M1–M6）+ 收尾；每模块附单测用例与两节点构建门禁。
- 2026-07-02：逐模块完成实装并逐步同步本档。M1–M3（数值阶段，门禁 G1）+ M4–M6（符号阶段，门禁 G2）均已完成；各模块先写代码+单测、两节点 build 绿后再勾选。收尾 F1/F2/F4 完成（F3 README 示例可选、暂缓）。实现中的关键决策：① 数值题型均限定为整数答案（多项式/定制模板），免玩家输无理数；② 解析器一元负高于 `^`（`-2^2=-4`）、`^` 右结合（`2^3^2=512`）；③ 变量指数（`e^x`/`2^x`）按 `a^{f(x)}=e^{f·ln a}` 处理；④ 裸 `√x` 歧义，约定需括号/花括号（`\sqrt{x}`/`√(x)`）。i18n（M3.7/M6.3）延后至 P8-A。
