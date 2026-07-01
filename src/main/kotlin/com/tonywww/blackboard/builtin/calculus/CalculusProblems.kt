package com.tonywww.blackboard.builtin.calculus

import net.minecraft.util.RandomSource

/** 题型。 */
enum class ProblemType { DIFF, INDEF_INTEGRAL, DEF_INTEGRAL, DERIV_AT_POINT, LIMIT }

/** 标准答案形态：一个数 / 一个表达式。 */
enum class AnswerKind { NUMBER, EXPRESSION }

/**
 * 一道生成好的微积分题（见 `docs/references/calculus-module.md` §3）。
 *
 * `answerKind=NUMBER` 用 `answerNumber`（判题走 `Validators.number`）；
 * `answerKind=EXPRESSION` 用 `answerInfix` + 采样区间（阶段二抽样等价判题）。
 * `fn`/`at`/`intA`/`intB` 为内存内测试/自洽辅助字段，不进 NBT。
 */
data class CalculusProblem(
    val type: ProblemType,
    val stemLatex: String,
    val stemInfix: String,
    val answerKind: AnswerKind,
    val answerNumber: Double? = null,
    val answerInfix: String? = null,
    val sampleLo: Double = 1.0,
    val sampleHi: Double = 3.0,
    val integral: Boolean = false,
    internal val fn: Expr? = null,      // 被处理的函数（求导题的 f / 积分题的被积函数 / 极限表达式）
    internal val at: Double? = null,    // DERIV_AT_POINT 的 x0 / LIMIT 趋近点（∞ 用 +Inf）
    internal val intA: Double? = null,  // DEF_INTEGRAL 下界
    internal val intB: Double? = null,  // DEF_INTEGRAL 上界
)

/**
 * 微积分模块 · 出题逻辑（三类拆分之「出题逻辑」）。
 *
 * 阶段一：数值答案题型 —— G 某点导数值 / I 定积分 / J 极限；均构造为**整数答案**，
 * 使 `Validators.number` 稳健判定；纯逻辑，仅依赖 [RandomSource] 与 [Expr]。
 */
object CalculusProblems {

    /** G 某点导数值：在整数 `x₀` 处求 `f'(x₀)`（整数）。难度提升次数/系数，并引入积法则（乘积）/链式（幂）。 */
    fun derivativeAtPoint(r: RandomSource, difficulty: Int): CalculusProblem {
        val d = difficulty.coerceIn(0, 10)
        val f = derivTarget(r, d)
        val x0 = r.nextIntBetweenInclusive(1, 3).toDouble()
        val ans = f.derivative().simplify().eval(x0)
        return CalculusProblem(
            type = ProblemType.DERIV_AT_POINT,
            stemLatex = "\\left." + diffLatex(f) + "\\right|_{x=" + fmtInt(x0) + "}",
            stemInfix = "d/dx(" + f.toInfix() + ") | x=" + fmtInt(x0),
            answerKind = AnswerKind.NUMBER,
            answerNumber = ans,
            fn = f, at = x0,
        )
    }

    /** 点导数值的被求导函数（整数点处导数为整数）：多项式 / 多项式乘积（积法则）/ 多项式幂（链式）。 */
    private fun derivTarget(r: RandomSource, difficulty: Int): Expr = when {
        difficulty <= 1 -> randomPoly(r, r.nextIntBetweenInclusive(2, 3 + difficulty).coerceAtMost(5), minPow = 0, difficulty = difficulty)
        else -> when (r.nextInt(3)) {
            0 -> randomPoly(r, r.nextIntBetweenInclusive(2, 3 + difficulty / 3).coerceAtMost(6), minPow = 0, difficulty = difficulty)
            1 -> Mul( // 乘积 → 积法则
                randomPoly(r, r.nextIntBetweenInclusive(1, 2 + difficulty / 4).coerceAtMost(3), minPow = 0, difficulty = difficulty),
                randomPoly(r, r.nextIntBetweenInclusive(1, 2 + difficulty / 4).coerceAtMost(3), minPow = 0, difficulty = difficulty),
            )
            else -> Pow( // 多项式幂 (x²+c)ⁿ → 链式
                Add(Pow(Var, 2.0), Const(r.nextIntBetweenInclusive(1, 3).toDouble())),
                r.nextIntBetweenInclusive(2, 2 + difficulty / 3).coerceAtMost(4).toDouble(),
            )
        }
    }

    /** I 定积分：被积 f 与精确答案 ∫ₐᵇ f dx。多项式(整数) / 多项式相乘(分数) / (x²+c)ⁿ 代换(整数) / 分式 c/x²(分数)。 */
    fun definiteIntegral(r: RandomSource, difficulty: Int): CalculusProblem {
        val d = difficulty.coerceIn(0, 10)
        val a = r.nextIntBetweenInclusive(1, 2).toDouble() // a≥1：保证分式/负幂在 [a,b] 有定义
        val b = a + r.nextIntBetweenInclusive(1, 2 + d / 4)
        val (f, ans) = defIntProblem(r, d, a, b)
        return CalculusProblem(
            type = ProblemType.DEF_INTEGRAL,
            stemLatex = defIntLatex(f, a, b),
            stemInfix = defIntInfix(f, a, b),
            answerKind = AnswerKind.NUMBER,
            answerNumber = ans,
            fn = f, intA = a, intB = b,
        )
    }

    /** 定积分的被积 f 与精确定积分值：多项式 / 多项式相乘 / (x²+c)ⁿ 代换 / 分式 c/x²。 */
    private fun defIntProblem(r: RandomSource, difficulty: Int, a: Double, b: Double): Pair<Expr, Double> =
        when (if (difficulty <= 1) 0 else r.nextInt(4)) {
            1 -> { // 多项式相乘 P·Q：展示为乘积，系数卷积算精确 ∫（分数答案）
                val (pE, pc) = randomPolyWithCoeffs(r, r.nextIntBetweenInclusive(1, 2 + difficulty / 4).coerceAtMost(3), difficulty)
                val (qE, qc) = randomPolyWithCoeffs(r, r.nextIntBetweenInclusive(1, 2 + difficulty / 4).coerceAtMost(3), difficulty)
                Pair(Mul(pE, qE), integrateCoeffs(mulCoeffs(pc, qc), a, b))
            }
            2 -> { // (x²+c)ⁿ u-代换（整数答案）
                val bigF = Pow(
                    Add(Pow(Var, 2.0), Const(r.nextIntBetweenInclusive(1, 3).toDouble())),
                    r.nextIntBetweenInclusive(2, 2 + difficulty / 3).coerceAtMost(3).toDouble(),
                )
                Pair(bigF.derivative().simplify(), bigF.eval(b) - bigF.eval(a))
            }
            3 -> { // 分式 c/x²：∫ₐᵇ c/x² dx = c(1/a - 1/b)（分数答案）
                val c = r.nextIntBetweenInclusive(1, 3 + difficulty)
                Pair(Div(Const(c.toDouble()), Pow(Var, 2.0)), c * (1.0 / a - 1.0 / b))
            }
            else -> { // 多项式（整数答案）：F 整系数、无常数项
                val bigF = randomPoly(r, r.nextIntBetweenInclusive(2, 3 + difficulty / 2).coerceAtMost(6), minPow = 1, difficulty = difficulty)
                Pair(bigF.derivative().simplify(), bigF.eval(b) - bigF.eval(a))
            }
        }

    /** 随机整系数多项式：返回展示用 [Expr] 与系数数组（`coeffs[i]` = x^i 的系数）。 */
    private fun randomPolyWithCoeffs(r: RandomSource, deg: Int, difficulty: Int): Pair<Expr, IntArray> {
        val c = IntArray(deg + 1)
        for (p in 0..deg) {
            c[p] = if (p == deg) r.nextIntBetweenInclusive(1, 3 + difficulty) * (if (r.nextBoolean()) 1 else -1)
            else r.nextIntBetweenInclusive(-(2 + difficulty), 2 + difficulty)
        }
        if (c.all { it == 0 }) c[deg] = 1
        val pairs = ArrayList<Pair<Int, Int>>()
        for (p in deg downTo 0) if (c[p] != 0) pairs.add(c[p] to p)
        if (pairs.isEmpty()) pairs.add(1 to 0)
        return Pair(assemblePoly(pairs), c)
    }

    /** 多项式系数卷积（乘法）。 */
    private fun mulCoeffs(x: IntArray, y: IntArray): IntArray {
        val z = IntArray(x.size + y.size - 1)
        for (i in x.indices) for (j in y.indices) z[i + j] += x[i] * y[j]
        return z
    }

    /** ∫ₐᵇ Σ c[i]x^i dx = Σ c[i]/(i+1)·(b^(i+1) - a^(i+1))（精确有理值，以 Double 累加）。 */
    private fun integrateCoeffs(c: IntArray, a: Double, b: Double): Double {
        var sum = 0.0
        for (i in c.indices) {
            if (c[i] == 0) continue
            var bp = 1.0; repeat(i + 1) { bp *= b }
            var ap = 1.0; repeat(i + 1) { ap *= a }
            sum += c[i].toDouble() / (i + 1) * (bp - ap)
        }
        return sum
    }

    /** J 极限：①连续代入 / ②`0/0` 因式相消 / ③`x→∞` 有理式；均整数答案。难度提升系数/根/比值。 */
    fun limit(r: RandomSource, difficulty: Int): CalculusProblem {
        val d = difficulty.coerceIn(0, 10)
        return when (r.nextInt(4)) {
            0 -> { // 连续代入 lim_{x→a} f = f(a)（多项式，次数随难度）
                val f = randomPoly(r, r.nextIntBetweenInclusive(1, 2 + d / 3).coerceAtMost(4), minPow = 0, difficulty = d)
                val a = r.nextIntBetweenInclusive(1, 4).toDouble()
                CalculusProblem(
                    ProblemType.LIMIT, limitLatex(f, a), limitInfix(f, a),
                    AnswerKind.NUMBER, answerNumber = f.eval(a), fn = f, at = a,
                )
            }
            1 -> { // 0/0 高次因式相消 (xⁿ-rⁿ)/(x-r) → n·rⁿ⁻¹（幂次 n 随难度增长）
                val n = r.nextIntBetweenInclusive(2, 2 + d / 3).coerceAtMost(5)
                val root = r.nextIntBetweenInclusive(1, 4 + d / 3)
                var rn = 1.0
                repeat(n) { rn *= root } // rⁿ（整数，精确）
                val expr = Div(Sub(Pow(Var, n.toDouble()), Const(rn)), Sub(Var, Const(root.toDouble())))
                val ans = n * (rn / root) // n·rⁿ⁻¹（整数）
                CalculusProblem(
                    ProblemType.LIMIT, limitLatex(expr, root.toDouble()), limitInfix(expr, root.toDouble()),
                    AnswerKind.NUMBER, answerNumber = ans, fn = expr, at = root.toDouble(),
                )
            }
            2 -> { // 0/0 一般二次因式分解 (x²-(p+q)x+pq)/(x-p) → p-q（展开显示，玩家须分解二次式）
                val p = r.nextIntBetweenInclusive(1, 4 + d / 3)
                val q = r.nextIntBetweenInclusive(1, 4 + d / 3)
                val num = Add(Sub(Pow(Var, 2.0), Mul(Const((p + q).toDouble()), Var)), Const((p * q).toDouble()))
                val expr = Div(num, Sub(Var, Const(p.toDouble())))
                CalculusProblem(
                    ProblemType.LIMIT, limitLatex(expr, p.toDouble()), limitInfix(expr, p.toDouble()),
                    AnswerKind.NUMBER, answerNumber = (p - q).toDouble(), fn = expr, at = p.toDouble(),
                )
            }
            else -> { // x→∞ 有理式（同次 n，含低次噪声项）→ 首系数比（构造成整数）
                val n = r.nextIntBetweenInclusive(2, 2 + d / 3).coerceAtMost(5)
                val bcoef = r.nextIntBetweenInclusive(1, 5)
                val ratio = r.nextIntBetweenInclusive(1, 3 + d / 2)
                val num = polyWithLeading(r, n, bcoef * ratio, d)
                val den = polyWithLeading(r, n, bcoef, d)
                val expr = Div(num, den)
                CalculusProblem(
                    ProblemType.LIMIT, limitInfLatex(expr), limitInfInfix(expr),
                    AnswerKind.NUMBER, answerNumber = ratio.toDouble(), fn = expr, at = Double.POSITIVE_INFINITY,
                )
            }
        }
    }

    /** 构造首项系数固定为 [lead]、次数为 [deg] 的多项式（含随机低次项；难度越高低次项越多/越大）。 */
    private fun polyWithLeading(r: RandomSource, deg: Int, lead: Int, difficulty: Int): Expr {
        var e: Expr = if (lead == 1) Pow(Var, deg.toDouble()) else Mul(Const(lead.toDouble()), Pow(Var, deg.toDouble()))
        for (pw in deg - 1 downTo 0) {
            val c = r.nextIntBetweenInclusive(-(3 + difficulty), 3 + difficulty)
            if (c == 0) continue
            val mag = kotlin.math.abs(c).toDouble()
            val term: Expr = when (pw) {
                0 -> Const(mag)
                1 -> Mul(Const(mag), Var)
                else -> Mul(Const(mag), Pow(Var, pw.toDouble()))
            }
            e = if (c < 0) Sub(e, term) else Add(e, term)
        }
        return e
    }
    /** A–F 求导（符号答案）：难度越高越深——高次多项式 → 基本/和差 → 积/商/链式 → 多项组合。答案为 `f'` 的规范 infix。 */
    fun differentiation(r: RandomSource, difficulty: Int): CalculusProblem {
        val f = diffTarget(r, difficulty.coerceIn(0, 10))
        val ans = f.derivative().simplify()
        return CalculusProblem(
            type = ProblemType.DIFF,
            stemLatex = diffLatex(f),
            stemInfix = diffInfix(f),
            answerKind = AnswerKind.EXPRESSION,
            answerInfix = ans.toInfix(),
            fn = f,
        )
    }

    /** 求导题目主体：难度越高越深——高次多项式 → 基本/和差 → 积/商/链式 → 深度嵌套复合（嵌套层数随难度增长）。 */
    private fun diffTarget(r: RandomSource, difficulty: Int): Expr = when {
        difficulty <= 0 -> randomPoly(r, r.nextIntBetweenInclusive(2, 3), minPow = 0, difficulty = 0)
        difficulty == 1 -> if (r.nextBoolean()) basicFn(r, difficulty) else sumFn(r, difficulty)
        difficulty == 2 -> combo(r, difficulty)
        else -> {
            // d>=3：深度嵌套复合函数——嵌套层数随难度增长（链式求导需层层套用）。
            val depth = (2 + (difficulty - 2) / 2).coerceIn(2, 6) // d3:2, d5:3, d7:4, d9:5, d10:6
            Mul(Const(coef(r, difficulty)), nested(r, depth, difficulty))
        }
    }

    /** 单个「较难」项：积 / 商 / 链式 / 高次单项（复杂度随难度）。 */
    private fun combo(r: RandomSource, difficulty: Int): Expr = when (r.nextInt(4)) {
        0 -> productFn(r, difficulty)
        1 -> quotientFn(r, difficulty)
        2 -> chainFn(r, difficulty)
        else -> Mul(Const(coef(r, difficulty)), Pow(Var, r.nextIntBetweenInclusive(2, 3 + difficulty / 3).coerceAtMost(6).toDouble()))
    }

    /**
     * 深度嵌套复合函数：在 [1,3] 上恒正、有定义、有界——因此可**任意层**包裹而不出 NaN/Inf（关键：
     * ln/sqrt 参数恒正；exp 只包 sin（参数锁在 [-1,1]）避免 exp 叠 exp 爆炸）。depth = 嵌套层数。
     */
    private fun nested(r: RandomSource, depth: Int, difficulty: Int): Expr {
        if (depth <= 0) return positiveBase(r, difficulty)
        val inner = nested(r, depth - 1, difficulty)
        return when (r.nextInt(8)) {
            0 -> Add(Sin(inner), Const(2.0)) // sin(inner)+2 ∈ [1,3]
            1 -> Add(Cos(inner), Const(2.0)) // cos(inner)+2 ∈ [1,3]
            2 -> Exp(Sin(inner)) // exp(sin(inner)) ∈ (0, e]
            3 -> Ln(Add(inner, Const(2.0))) // ln(inner+2)，inner≥0 → 参数≥2>0
            4 -> Sqrt(Add(inner, Const(1.0))) // sqrt(inner+1)，inner≥0 → 参数≥1>0
            5 -> Add(Pow(Sin(inner), 2.0), Const(1.0)) // sin²(inner)+1 ∈ [1,2]
            6 -> Div(Const(coef(r, difficulty)), Add(inner, Const(1.0))) // 分式 a/(inner+1)，分母≥1>0 → ∈(0,a]
            else -> Div(Const(1.0), Add(Pow(Sin(inner), 2.0), Const(1.0))) // 分式 1/(sin²(inner)+1) ∈ [0.5,1]
        }
    }

    /** 恒正基（[1,3] 上 > 0）：a x + b 或 x² + c。 */
    private fun positiveBase(r: RandomSource, difficulty: Int): Expr =
        if (r.nextBoolean()) Add(Mul(Const(coef(r, difficulty)), Var), Const(coef(r, difficulty)))
        else Add(Pow(Var, 2.0), Const(coef(r, difficulty)))

    /** H 不定积分（逆向）：造原函数 `F`，被积 `f=F'`；答案为 `F` 的规范 infix（判题两点差消 +C）。 */
    fun indefiniteIntegral(r: RandomSource, difficulty: Int): CalculusProblem {
        val bigF = antiderivative(r, difficulty.coerceIn(0, 10))
        val f = bigF.derivative().simplify()
        return CalculusProblem(
            type = ProblemType.INDEF_INTEGRAL,
            stemLatex = indefIntLatex(f),
            stemInfix = indefIntInfix(f),
            answerKind = AnswerKind.EXPRESSION,
            answerInfix = bigF.toInfix(),
            integral = true,
            fn = f,
        )
    }
    // ---- 私有模板 ----

    /** 整数系数多项式：幂次 `deg..minPow`，首项非零。难度放宽系数幅度。用 Add/Sub 按符号拼装以获得可读题面。 */
    private fun randomPoly(r: RandomSource, deg: Int, minPow: Int, difficulty: Int = 0): Expr {
        val leadMax = 4 + difficulty
        val range = 5 + difficulty
        val coeffs = ArrayList<Pair<Int, Int>>()
        for (p in deg downTo minPow) {
            val c = if (p == deg) {
                r.nextIntBetweenInclusive(1, leadMax) * (if (r.nextBoolean()) 1 else -1)
            } else {
                r.nextIntBetweenInclusive(-range, range)
            }
            if (c != 0) coeffs.add(c to p)
        }
        if (coeffs.isEmpty()) coeffs.add(1 to deg)
        return assemblePoly(coeffs)
    }

    private fun assemblePoly(coeffs: List<Pair<Int, Int>>): Expr {
        var e: Expr? = null
        for ((c, p) in coeffs) {
            val mag = kotlin.math.abs(c)
            val term: Expr = when {
                p == 0 -> Const(mag.toDouble())
                mag == 1 && p == 1 -> Var
                mag == 1 -> Pow(Var, p.toDouble())
                p == 1 -> Mul(Const(mag.toDouble()), Var)
                else -> Mul(Const(mag.toDouble()), Pow(Var, p.toDouble()))
            }
            e = when {
                e == null -> if (c < 0) Neg(term) else term
                c < 0 -> Sub(e, term)
                else -> Add(e, term)
            }
        }
        return e ?: ZERO
    }

    private fun fmtInt(d: Double) = d.toLong().toString()

    private fun coef(r: RandomSource, difficulty: Int = 0) = r.nextIntBetweenInclusive(1, 4 + difficulty).toDouble()

    /** 单个基本函数（系数×{幂/sin/cos/exp/ln/sqrt}），在 [1,3] 有定义。难度放宽幂次/系数。 */
    private fun basicFn(r: RandomSource, difficulty: Int = 0): Expr = when (r.nextInt(6)) {
        0 -> Mul(Const(coef(r, difficulty)), Pow(Var, r.nextIntBetweenInclusive(2, 3 + difficulty / 3).coerceAtMost(6).toDouble()))
        1 -> Mul(Const(coef(r, difficulty)), Sin(Var))
        2 -> Mul(Const(coef(r, difficulty)), Cos(Var))
        3 -> Mul(Const(coef(r, difficulty)), Exp(Var))
        4 -> Mul(Const(coef(r, difficulty)), Ln(Var))
        else -> Mul(Const(coef(r, difficulty)), Sqrt(Var))
    }

    private fun sumFn(r: RandomSource, difficulty: Int = 0): Expr {
        val terms = 2 + difficulty / 3 // d0:2, d3:3, d6:4, d9:5
        var e = basicFn(r, difficulty)
        repeat(terms - 1) {
            val t = basicFn(r, difficulty)
            e = if (r.nextBoolean()) Add(e, t) else Sub(e, t)
        }
        return e
    }

    private fun simpleFactor(r: RandomSource, difficulty: Int = 0): Expr = when (r.nextInt(4)) {
        0 -> Mul(Const(coef(r, difficulty)), Pow(Var, r.nextIntBetweenInclusive(1, 2 + difficulty / 4).coerceAtMost(4).toDouble()))
        1 -> Sin(Var)
        2 -> Cos(Var)
        else -> Exp(Var)
    }

    private fun productFn(r: RandomSource, difficulty: Int = 0): Expr = Mul(simpleFactor(r, difficulty), simpleFactor(r, difficulty))

    private fun quotientFn(r: RandomSource, difficulty: Int = 0): Expr =
        Div(simpleFactor(r, difficulty), Add(Pow(Var, 2.0), Const(coef(r, difficulty)))) // 恒正分母 x^2 + c

    private fun chainFn(r: RandomSource, difficulty: Int = 0): Expr {
        val a = r.nextIntBetweenInclusive(2, 4)
        val b = r.nextIntBetweenInclusive(1, 4)
        val inner = Add(Mul(Const(a.toDouble()), Var), Const(b.toDouble())) // a x + b > 0 on [1,3]
        return when (r.nextInt(6)) {
            0 -> Sin(inner)
            1 -> Cos(inner)
            2 -> Exp(inner)
            3 -> Ln(inner)
            4 -> Sqrt(inner)
            else -> Pow(inner, r.nextIntBetweenInclusive(2, 3 + difficulty / 4).coerceAtMost(5).toDouble())
        }
    }

    /**
     * 逆向积分的原函数 `F`：多个「可积项」之和（项数随难度增长），每项为多项式 / a·sin(kx+b) /
     * a·cos(kx+b) / a·exp(kx+b) / a·sin / a·cos / a·exp / a·ln；均在 [1,3] 可导且 f=F' 有定义。难度提升项数/次数/系数。
     */
    private fun antiderivative(r: RandomSource, difficulty: Int = 0): Expr {
        val terms = (1 + difficulty / 3).coerceAtMost(4) // d0:1, d3:2, d6:3, d9:4
        var e = antiderivativeTerm(r, difficulty)
        repeat(terms - 1) {
            val t = antiderivativeTerm(r, difficulty)
            e = if (r.nextBoolean()) Add(e, t) else Sub(e, t)
        }
        return e
    }

    /** 单个可积项（其 F' 有干净原函数）：多项式 / a·{sin,cos,exp}(g) / a·sin / a·cos / a·exp / a·ln / a·ln(x²+c)（分式）。g 高难度可非线性。 */
    private fun antiderivativeTerm(r: RandomSource, difficulty: Int): Expr = when (r.nextInt(7)) {
        0 -> randomPoly(r, r.nextIntBetweenInclusive(2, 3 + difficulty / 3).coerceAtMost(6), minPow = 1, difficulty = difficulty)
        1 -> Mul(Const(coef(r, difficulty)), Sin(Var))
        2 -> Mul(Const(coef(r, difficulty)), Cos(Var))
        3 -> Mul(Const(coef(r, difficulty)), Exp(Var))
        4 -> Mul(Const(coef(r, difficulty)), Ln(Var)) // f = a/x（分式）
        5 -> Mul(Const(coef(r, difficulty)), Ln(Add(Pow(Var, 2.0), Const(coef(r, difficulty))))) // F=a·ln(x²+c) → f=2ax/(x²+c)（分式，u-代换）
        else -> {
            // 可积复合项 a·{sin,cos,exp}(g)：g = kx+b，高难度可取 x²+c（f=F' 为非线性 u-代换可解）。
            val inner = if (difficulty >= 4 && r.nextBoolean()) {
                Add(Pow(Var, 2.0), Const(r.nextIntBetweenInclusive(1, 3).toDouble())) // x² + c > 0
            } else {
                val k = r.nextIntBetweenInclusive(2, 3)
                val b = r.nextIntBetweenInclusive(1, 3)
                Add(Mul(Const(k.toDouble()), Var), Const(b.toDouble())) // k x + b > 0 on [1,3]
            }
            val a = Const(coef(r, difficulty))
            when (r.nextInt(3)) {
                0 -> Mul(a, Sin(inner))
                1 -> Mul(a, Cos(inner))
                else -> Mul(a, Exp(inner))
            }
        }
    }
}
