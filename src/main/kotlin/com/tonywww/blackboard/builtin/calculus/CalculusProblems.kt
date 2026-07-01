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

    /** G 某点导数值：整数系数多项式 `f`，在整数 `x₀` 处求 `f'(x₀)`（整数）。 */
    fun derivativeAtPoint(r: RandomSource, difficulty: Int): CalculusProblem {
        val deg = if (difficulty <= 0) r.nextIntBetweenInclusive(2, 3) else r.nextIntBetweenInclusive(2, 4)
        val f = randomPoly(r, deg, minPow = 0)
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

    /** I 定积分（逆向）：整数系数原函数 `F`，被积 `f=F'`，整数界 `[a,b]`，答案 `F(b)-F(a)`（整数）。 */
    fun definiteIntegral(r: RandomSource, difficulty: Int): CalculusProblem {
        val degF = r.nextIntBetweenInclusive(1, 3)
        val bigF = randomPoly(r, degF, minPow = 1) // 无常数项（F(b)-F(a) 中会抵消）
        val f = bigF.derivative().simplify()
        val a = r.nextIntBetweenInclusive(0, 2).toDouble()
        val b = a + r.nextIntBetweenInclusive(1, 3)
        val ans = bigF.eval(b) - bigF.eval(a)
        return CalculusProblem(
            type = ProblemType.DEF_INTEGRAL,
            stemLatex = defIntLatex(f, a, b),
            stemInfix = defIntInfix(f, a, b),
            answerKind = AnswerKind.NUMBER,
            answerNumber = ans,
            fn = f, intA = a, intB = b,
        )
    }

    /** J 极限：①连续代入 / ②`0/0` 因式相消 / ③`x→∞` 有理式；均整数答案。 */
    fun limit(r: RandomSource, difficulty: Int): CalculusProblem = when (r.nextInt(3)) {
        0 -> { // 连续代入 lim_{x→a} f = f(a)
            val f = randomPoly(r, r.nextIntBetweenInclusive(1, 2), minPow = 0)
            val a = r.nextIntBetweenInclusive(1, 4).toDouble()
            CalculusProblem(
                ProblemType.LIMIT, limitLatex(f, a), limitInfix(f, a),
                AnswerKind.NUMBER, answerNumber = f.eval(a), fn = f, at = a,
            )
        }
        1 -> { // 0/0 因式相消 (x²-r²)/(x-r) → 2r
            val root = r.nextIntBetweenInclusive(1, 5)
            val expr = Div(Sub(Pow(Var, 2.0), Const((root * root).toDouble())), Sub(Var, Const(root.toDouble())))
            CalculusProblem(
                ProblemType.LIMIT, limitLatex(expr, root.toDouble()), limitInfix(expr, root.toDouble()),
                AnswerKind.NUMBER, answerNumber = (2 * root).toDouble(), fn = expr, at = root.toDouble(),
            )
        }
        else -> { // x→∞ 有理式（同次）→ 首系数比（构造成整数）
            val n = r.nextIntBetweenInclusive(1, 3).toDouble()
            val bcoef = r.nextIntBetweenInclusive(1, 5)
            val ratio = r.nextIntBetweenInclusive(1, 5)
            val acoef = bcoef * ratio
            val num = Add(Mul(Const(acoef.toDouble()), Pow(Var, n)), Const(r.nextIntBetweenInclusive(-5, 5).toDouble()))
            val den = Add(Mul(Const(bcoef.toDouble()), Pow(Var, n)), Const(r.nextIntBetweenInclusive(-5, 5).toDouble()))
            val expr = Div(num, den)
            CalculusProblem(
                ProblemType.LIMIT, limitInfLatex(expr), limitInfInfix(expr),
                AnswerKind.NUMBER, answerNumber = ratio.toDouble(), fn = expr, at = Double.POSITIVE_INFINITY,
            )
        }
    }
    /** A–F 求导（符号答案）：按难度选多项式/基本/和差/积/商/链式；答案为 `f'` 的规范 infix。 */
    fun differentiation(r: RandomSource, difficulty: Int): CalculusProblem {
        val f = when {
            difficulty <= 0 -> randomPoly(r, r.nextIntBetweenInclusive(2, 3), minPow = 0)
            difficulty == 1 -> if (r.nextBoolean()) basicFn(r) else sumFn(r)
            difficulty == 2 -> when (r.nextInt(3)) {
                0 -> productFn(r)
                1 -> quotientFn(r)
                else -> chainFn(r)
            }
            else -> chainFn(r)
        }
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

    /** H 不定积分（逆向）：造原函数 `F`，被积 `f=F'`；答案为 `F` 的规范 infix（判题两点差消 +C）。 */
    fun indefiniteIntegral(r: RandomSource, difficulty: Int): CalculusProblem {
        val bigF = antiderivative(r)
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

    /** 整数系数多项式：幂次 `deg..minPow`，首项非零。用 Add/Sub 按符号拼装以获得可读题面。 */
    private fun randomPoly(r: RandomSource, deg: Int, minPow: Int): Expr {
        val coeffs = ArrayList<Pair<Int, Int>>()
        for (p in deg downTo minPow) {
            val c = if (p == deg) {
                r.nextIntBetweenInclusive(1, 5) * (if (r.nextBoolean()) 1 else -1)
            } else {
                r.nextIntBetweenInclusive(-5, 5)
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

    private fun coef(r: RandomSource) = r.nextIntBetweenInclusive(1, 5).toDouble()

    /** 单个基本函数（系数×{幂/sin/cos/exp/ln/sqrt}），在 [1,3] 有定义。 */
    private fun basicFn(r: RandomSource): Expr = when (r.nextInt(6)) {
        0 -> Mul(Const(coef(r)), Pow(Var, r.nextIntBetweenInclusive(2, 3).toDouble()))
        1 -> Mul(Const(coef(r)), Sin(Var))
        2 -> Mul(Const(coef(r)), Cos(Var))
        3 -> Mul(Const(coef(r)), Exp(Var))
        4 -> Mul(Const(coef(r)), Ln(Var))
        else -> Mul(Const(coef(r)), Sqrt(Var))
    }

    private fun sumFn(r: RandomSource): Expr {
        val a = basicFn(r)
        val b = basicFn(r)
        return if (r.nextBoolean()) Add(a, b) else Sub(a, b)
    }

    private fun simpleFactor(r: RandomSource): Expr = when (r.nextInt(4)) {
        0 -> Mul(Const(coef(r)), Pow(Var, r.nextIntBetweenInclusive(1, 2).toDouble()))
        1 -> Sin(Var)
        2 -> Cos(Var)
        else -> Exp(Var)
    }

    private fun productFn(r: RandomSource): Expr = Mul(simpleFactor(r), simpleFactor(r))

    private fun quotientFn(r: RandomSource): Expr =
        Div(simpleFactor(r), Add(Pow(Var, 2.0), Const(coef(r)))) // 恒正分母 x^2 + c

    private fun chainFn(r: RandomSource): Expr {
        val a = r.nextIntBetweenInclusive(2, 4)
        val b = r.nextIntBetweenInclusive(1, 4)
        val inner = Add(Mul(Const(a.toDouble()), Var), Const(b.toDouble())) // a x + b > 0 on [1,3]
        return when (r.nextInt(6)) {
            0 -> Sin(inner)
            1 -> Cos(inner)
            2 -> Exp(inner)
            3 -> Ln(inner)
            4 -> Sqrt(inner)
            else -> Pow(inner, r.nextIntBetweenInclusive(2, 3).toDouble())
        }
    }

    /** 逆向积分的原函数 `F`（多项式 / a·sin / a·cos / a·exp / a·ln）；均在 [1,3] 可导且 f=F' 有定义。 */
    private fun antiderivative(r: RandomSource): Expr = when (r.nextInt(5)) {
        0 -> randomPoly(r, r.nextIntBetweenInclusive(2, 3), minPow = 1)
        1 -> Mul(Const(coef(r)), Sin(Var))
        2 -> Mul(Const(coef(r)), Cos(Var))
        3 -> Mul(Const(coef(r)), Exp(Var))
        else -> Mul(Const(coef(r)), Ln(Var)) // f = a/x
    }
}
