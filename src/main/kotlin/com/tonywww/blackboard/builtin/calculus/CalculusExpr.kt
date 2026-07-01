package com.tonywww.blackboard.builtin.calculus

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 微积分模块 · 表达式模型 + LaTeX/文本渲染（三类拆分之「LaTeX / 表达式」，见
 * `docs/references/calculus-module.md`）。
 *
 * 变量固定为 `x`；节点不可变（`data class`/`object`），便于结构相等与纯单测。
 * 阶段一（零依赖）：AST + `eval`/`derivative`/`simplify` + `toInfix`/`toLatex` + 题型外壳。
 * 阶段二再在本文件扩展 LaTeX 归一化 + infix 解析器（`ExpressionEvaluator`）。
 */
sealed interface Expr {
    /** 数值求值（变量取 [x]）。 */
    fun eval(x: Double): Double

    /** 符号求导（未化简）。 */
    fun derivative(): Expr

    /** 规范 infix（紧凑、可被阶段二解析器吃回）。 */
    fun toInfix(): String

    /** 显示用 LaTeX。 */
    fun toLatex(): String
}

data class Const(val c: Double) : Expr {
    override fun eval(x: Double) = c
    override fun derivative(): Expr = ZERO
    override fun toInfix() = fmtNum(c)
    override fun toLatex() = fmtNum(c)
}

object Var : Expr {
    override fun eval(x: Double) = x
    override fun derivative(): Expr = ONE
    override fun toInfix() = "x"
    override fun toLatex() = "x"
}

data class Neg(val e: Expr) : Expr {
    override fun eval(x: Double) = -e.eval(x)
    override fun derivative() = Neg(e.derivative())
    override fun toInfix() = "-" + wrapI(e, 2)
    override fun toLatex() = "-" + wrapL(e, 2)
}

data class Add(val a: Expr, val b: Expr) : Expr {
    override fun eval(x: Double) = a.eval(x) + b.eval(x)
    override fun derivative() = Add(a.derivative(), b.derivative())
    override fun toInfix() = wrapI(a, 1) + "+" + wrapI(b, 1)
    override fun toLatex() = wrapL(a, 1) + " + " + wrapL(b, 1)
}

data class Sub(val a: Expr, val b: Expr) : Expr {
    override fun eval(x: Double) = a.eval(x) - b.eval(x)
    override fun derivative() = Sub(a.derivative(), b.derivative())
    override fun toInfix() = wrapI(a, 1) + "-" + wrapI(b, 2)
    override fun toLatex() = wrapL(a, 1) + " - " + wrapL(b, 2)
}

data class Mul(val a: Expr, val b: Expr) : Expr {
    override fun eval(x: Double) = a.eval(x) * b.eval(x)

    // 乘积法则：u'v + uv'
    override fun derivative() = Add(Mul(a.derivative(), b), Mul(a, b.derivative()))
    override fun toInfix() = wrapI(a, 2) + "*" + wrapI(b, 2)
    override fun toLatex() = mulLatex(a, b)
}

data class Div(val a: Expr, val b: Expr) : Expr {
    override fun eval(x: Double) = a.eval(x) / b.eval(x)

    // 商法则：(u'v - uv') / v^2
    override fun derivative() =
        Div(Sub(Mul(a.derivative(), b), Mul(a, b.derivative())), Pow(b, 2.0))

    override fun toInfix() = wrapI(a, 2) + "/" + wrapI(b, 3)
    override fun toLatex() = "\\frac{" + a.toLatex() + "}{" + b.toLatex() + "}"
}

/** 幂 `base^exp`，指数为常数（整数/有理）。 */
data class Pow(val base: Expr, val exp: Double) : Expr {
    override fun eval(x: Double) = base.eval(x).pow(exp)

    // 幂法则 + 链式：n·base^(n-1)·base'
    override fun derivative() =
        Mul(Mul(Const(exp), Pow(base, exp - 1.0)), base.derivative())

    override fun toInfix() = wrapI(base, 4) + "^" + powExpI(exp)
    override fun toLatex() = wrapL(base, 4) + "^{" + fmtNum(exp) + "}"
}

data class Sin(val e: Expr) : Expr {
    override fun eval(x: Double) = sin(e.eval(x))
    override fun derivative() = Mul(Cos(e), e.derivative())
    override fun toInfix() = "sin(" + e.toInfix() + ")"
    override fun toLatex() = "\\sin\\left(" + e.toLatex() + "\\right)"
}

data class Cos(val e: Expr) : Expr {
    override fun eval(x: Double) = cos(e.eval(x))
    override fun derivative() = Neg(Mul(Sin(e), e.derivative()))
    override fun toInfix() = "cos(" + e.toInfix() + ")"
    override fun toLatex() = "\\cos\\left(" + e.toLatex() + "\\right)"
}

data class Exp(val e: Expr) : Expr {
    override fun eval(x: Double) = exp(e.eval(x))
    override fun derivative() = Mul(Exp(e), e.derivative())
    override fun toInfix() = "exp(" + e.toInfix() + ")"
    override fun toLatex() = "e^{" + e.toLatex() + "}"
}

data class Ln(val e: Expr) : Expr {
    override fun eval(x: Double) = ln(e.eval(x))
    override fun derivative() = Div(e.derivative(), e)
    override fun toInfix() = "ln(" + e.toInfix() + ")"
    override fun toLatex() = "\\ln\\left(" + e.toLatex() + "\\right)"
}

data class Sqrt(val e: Expr) : Expr {
    override fun eval(x: Double) = sqrt(e.eval(x))
    override fun derivative() = Div(e.derivative(), Mul(Const(2.0), Sqrt(e)))
    override fun toInfix() = "sqrt(" + e.toInfix() + ")"
    override fun toLatex() = "\\sqrt{" + e.toLatex() + "}"
}

// ---- 常量 ----
val ZERO = Const(0.0)
val ONE = Const(1.0)

// ---- 化简（0/1 恒等、常数折叠、去多余负号；幂等，见 simplifyOnce）----

fun Expr.simplify(): Expr {
    var e = this
    repeat(8) {
        val next = e.simplifyOnce()
        if (next == e) return next
        e = next
    }
    return e
}

private fun Expr.isZero() = this is Const && c == 0.0
private fun Expr.isOne() = this is Const && c == 1.0

private fun Expr.simplifyOnce(): Expr = when (this) {
    is Const, is Var -> this
    is Neg -> {
        val s = e.simplifyOnce()
        when {
            s is Const -> Const(-s.c)
            s is Neg -> s.e
            else -> Neg(s)
        }
    }
    is Add -> {
        val sa = a.simplifyOnce(); val sb = b.simplifyOnce()
        when {
            sa is Const && sb is Const -> Const(sa.c + sb.c)
            sa.isZero() -> sb
            sb.isZero() -> sa
            else -> Add(sa, sb)
        }
    }
    is Sub -> {
        val sa = a.simplifyOnce(); val sb = b.simplifyOnce()
        when {
            sa is Const && sb is Const -> Const(sa.c - sb.c)
            sb.isZero() -> sa
            sa.isZero() -> Neg(sb)
            else -> Sub(sa, sb)
        }
    }
    is Mul -> {
        val sa = a.simplifyOnce(); val sb = b.simplifyOnce()
        when {
            sa is Const && sb is Const -> Const(sa.c * sb.c)
            sa.isZero() || sb.isZero() -> ZERO
            sa.isOne() -> sb
            sb.isOne() -> sa
            else -> Mul(sa, sb)
        }
    }
    is Div -> {
        val sa = a.simplifyOnce(); val sb = b.simplifyOnce()
        when {
            sa.isZero() -> ZERO
            sb.isOne() -> sa
            sa is Const && sb is Const && sb.c != 0.0 -> Const(sa.c / sb.c)
            else -> Div(sa, sb)
        }
    }
    is Pow -> {
        val sbase = base.simplifyOnce()
        when {
            exp == 0.0 -> ONE
            exp == 1.0 -> sbase
            sbase is Const -> Const(sbase.c.pow(exp))
            else -> Pow(sbase, exp)
        }
    }
    is Sin -> Sin(e.simplifyOnce())
    is Cos -> Cos(e.simplifyOnce())
    is Exp -> Exp(e.simplifyOnce())
    is Ln -> Ln(e.simplifyOnce())
    is Sqrt -> Sqrt(e.simplifyOnce())
}

// ---- 题型外壳（拼接题面）----

fun diffLatex(f: Expr) = "\\frac{d}{dx}\\left(" + f.toLatex() + "\\right)"
fun diffInfix(f: Expr) = "d/dx(" + f.toInfix() + ")"
fun indefIntLatex(f: Expr) = "\\int " + f.toLatex() + " \\,dx"
fun indefIntInfix(f: Expr) = "int " + f.toInfix() + " dx"
fun defIntLatex(f: Expr, a: Double, b: Double) =
    "\\int_{" + fmtNum(a) + "}^{" + fmtNum(b) + "} " + f.toLatex() + " \\,dx"
fun defIntInfix(f: Expr, a: Double, b: Double) =
    "int[" + fmtNum(a) + "," + fmtNum(b) + "] " + f.toInfix() + " dx"
fun limitLatex(f: Expr, a: Double) = "\\lim_{x \\to " + fmtNum(a) + "} " + f.toLatex()
fun limitInfix(f: Expr, a: Double) = "lim x->" + fmtNum(a) + " (" + f.toInfix() + ")"
fun limitInfLatex(f: Expr) = "\\lim_{x \\to \\infty} " + f.toLatex()
fun limitInfInfix(f: Expr) = "lim x->inf (" + f.toInfix() + ")"

// ---- 渲染辅助（优先级括号）----

private fun precOf(e: Expr): Int = when (e) {
    is Add, is Sub -> 1
    is Mul, is Div, is Neg -> 2
    is Pow -> 3
    else -> 4 // Const, Var, Sin, Cos, Exp, Ln, Sqrt（原子 / 函数调用）
}

private fun wrapI(e: Expr, min: Int): String =
    if (precOf(e) < min) "(" + e.toInfix() + ")" else e.toInfix()

private fun wrapL(e: Expr, min: Int): String =
    if (precOf(e) < min) "\\left(" + e.toLatex() + "\\right)" else e.toLatex()

private fun mulLatex(a: Expr, b: Expr): String {
    val la = wrapL(a, 2); val lb = wrapL(b, 2)
    return if (a is Const && b is Const) "$la \\cdot $lb" else "$la $lb"
}

/** 整数值不带小数点；负指数 infix 加括号以便解析。 */
private fun fmtNum(c: Double): String =
    if (c == c.toLong().toDouble()) c.toLong().toString() else c.toString()

private fun powExpI(exp: Double): String {
    val s = fmtNum(exp)
    return if (exp < 0) "($s)" else s
}

// ==== 阶段二：LaTeX 归一化 + infix 解析 + 求值器 + 抽样判等 ====

/** 把 LaTeX 子集归一化为可解析的 infix（花括号感知）。 */
fun normalizeLatex(input: String): String {
    var s = input
    s = s.replace("\\left", "").replace("\\right", "")
    s = s.replace("\\,", "").replace("\\!", "").replace("\\;", "").replace("\\quad", " ")
    s = s.replace("\\cdot", "*").replace("\\times", "*")
    s = s.replace("\\sin", "sin").replace("\\cos", "cos").replace("\\tan", "tan")
    s = s.replace("\\ln", "ln").replace("\\exp", "exp").replace("\\sqrt", "sqrt").replace("\\pi", "pi")
    s = s.replace("√", "sqrt").replace("π", "pi").replace("·", "*").replace("×", "*").replace("−", "-")
    var prev: String
    do {
        prev = s
        s = replaceFrac(s)
        s = braceCall(s, "sqrt")
        s = caretBrace(s)
    } while (s != prev)
    return s
}

private fun matchBrace(s: String, open: Int): Int {
    var depth = 0
    var i = open
    while (i < s.length) {
        when (s[i]) {
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) return i }
        }
        i++
    }
    return -1
}

private fun replaceFrac(s0: String): String {
    var s = s0
    while (true) {
        val idx = s.indexOf("\\frac")
        if (idx < 0) return s
        val b1 = s.indexOf('{', idx); if (b1 < 0) return s
        val e1 = matchBrace(s, b1); if (e1 < 0) return s
        val b2 = s.indexOf('{', e1 + 1); if (b2 < 0) return s
        val e2 = matchBrace(s, b2); if (e2 < 0) return s
        s = s.substring(0, idx) + "((" + s.substring(b1 + 1, e1) + ")/(" + s.substring(b2 + 1, e2) + "))" +
            s.substring(e2 + 1)
    }
}

private fun braceCall(s0: String, name: String): String {
    var s = s0
    while (true) {
        val idx = s.indexOf("$name{")
        if (idx < 0) return s
        val b = idx + name.length
        val e = matchBrace(s, b); if (e < 0) return s
        s = s.substring(0, b) + "(" + s.substring(b + 1, e) + ")" + s.substring(e + 1)
    }
}

private fun caretBrace(s0: String): String {
    var s = s0
    while (true) {
        val idx = s.indexOf("^{")
        if (idx < 0) return s
        val b = idx + 1
        val e = matchBrace(s, b); if (e < 0) return s
        s = s.substring(0, b) + "(" + s.substring(b + 1, e) + ")" + s.substring(e + 1)
    }
}

/** 解析 infix 表达式为 [Expr]；非法返回 null。 */
fun parseExpr(input: String): Expr? = try {
    ExprParser(input).parseAll()
} catch (e: Exception) {
    null
}

/**
 * 递归下降解析器（零依赖）：`+ - * / ^`、一元负、**隐式乘法**、函数 `sin cos tan exp ln sqrt`、
 * 常量 `pi e`、变量 `x`。文法见 `docs/references/calculus-module.md` §2.5。
 */
private class ExprParser(src: String) {
    private val s = src.replace(" ", "")
    private var pos = 0
    private fun peek(): Char? = if (pos < s.length) s[pos] else null
    private fun expect(c: Char) { if (peek() == c) pos++ else error("expected $c") }

    fun parseAll(): Expr {
        val e = expr()
        if (pos != s.length) error("trailing @$pos")
        return e
    }

    private fun expr(): Expr {
        var e = term()
        while (peek() == '+' || peek() == '-') {
            val op = s[pos++]
            val t = term()
            e = if (op == '+') Add(e, t) else Sub(e, t)
        }
        return e
    }

    private fun term(): Expr {
        var e = unary()
        while (true) {
            val c = peek() ?: break
            e = when {
                c == '*' -> { pos++; Mul(e, unary()) }
                c == '/' -> { pos++; Div(e, unary()) }
                c.isLetterOrDigit() || c == '(' -> Mul(e, unary()) // 隐式乘法
                else -> break
            }
        }
        return e
    }

    private fun unary(): Expr = if (peek() == '-') { pos++; Neg(unary()) } else power()

    private fun power(): Expr {
        val b = base()
        if (peek() != '^') return b
        pos++
        val expExpr = unary()
        val cv = tryConst(expExpr)
        // 常数指数→ Pow；变量指数（如 e^x / 2^x）→ a^{f(x)} = e^{f·ln a}
        return if (cv != null) Pow(b, cv) else Exp(Mul(expExpr, Ln(b)))
    }

    private fun base(): Expr {
        val c = peek() ?: error("eof")
        if (c == '(') { pos++; val e = expr(); expect(')'); return e }
        if (c.isDigit() || c == '.') return Const(number())
        return when (val id = ident()) {
            "x" -> Var
            "pi" -> Const(PI)
            "e" -> Const(E)
            "sin", "cos", "tan", "exp", "ln", "sqrt" -> { expect('('); val a = expr(); expect(')'); fn(id, a) }
            else -> error("unknown id: $id")
        }
    }

    private fun number(): Double {
        val start = pos
        while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
        return s.substring(start, pos).toDouble()
    }

    private fun ident(): String {
        val start = pos
        while (peek()?.isLetter() == true) pos++
        if (pos == start) error("expected identifier @$pos")
        return s.substring(start, pos).lowercase()
    }

    private fun fn(id: String, a: Expr): Expr = when (id) {
        "sin" -> Sin(a)
        "cos" -> Cos(a)
        "exp" -> Exp(a)
        "ln" -> Ln(a)
        "sqrt" -> Sqrt(a)
        "tan" -> Div(Sin(a), Cos(a))
        else -> error("fn $id")
    }

    /** 指数若为常数（不依赖 x）则返回其值，否则 null。 */
    private fun tryConst(e: Expr): Double? {
        val v0 = e.eval(0.0)
        val v1 = e.eval(1.0)
        return if (v0 == v1 && v0.isFinite()) v0 else null
    }
}

/** [com.tonywww.blackboard.validation.ExpressionEvaluator] 实现：LaTeX/infix → 数值（`vars["x"]`）；失败/未定义返回 null。 */
object CalculusEvaluator : com.tonywww.blackboard.validation.ExpressionEvaluator {
    override fun eval(expr: String, vars: Map<String, Double>): Double? {
        val e = parseExpr(normalizeLatex(expr)) ?: return null
        val v = try { e.eval(vars["x"] ?: 0.0) } catch (ex: Exception) { return null }
        return if (v.isFinite()) v else null
    }
}

// ---- 抽样判等（判题原语）----

private fun finiteOrNull(e: Expr, x: Double): Double? =
    try { e.eval(x).takeIf { it.isFinite() } } catch (ex: Exception) { null }

/** 在 [lo,hi] 均匀取 [n] 点比较 std/got 取值；std 有定义而 got 无定义 → 判不等。 */
fun sampleEqual(std: Expr, got: Expr, lo: Double, hi: Double, n: Int = 16, tol: Double = 1e-6): Boolean {
    var valid = 0
    for (i in 0 until n) {
        val x = lo + (hi - lo) * (i + 0.5) / n
        val a = finiteOrNull(std, x) ?: continue
        val b = finiteOrNull(got, x) ?: return false
        valid++
        if (kotlin.math.abs(a - b) > tol * (1 + kotlin.math.abs(a))) return false
    }
    return valid >= 4
}

/** 两点差判等（消 +C）：比较 `std(x)-std(x0)` 与 `got(x)-got(x0)`。 */
fun sampleEqualUpToConst(std: Expr, got: Expr, lo: Double, hi: Double, n: Int = 16, tol: Double = 1e-6): Boolean {
    val x0 = (lo + hi) / 2
    val s0 = finiteOrNull(std, x0) ?: return false
    val g0 = finiteOrNull(got, x0) ?: return false
    var valid = 0
    for (i in 0 until n) {
        val x = lo + (hi - lo) * (i + 0.5) / n
        val a = finiteOrNull(std, x) ?: continue
        val b = finiteOrNull(got, x) ?: return false
        valid++
        if (kotlin.math.abs((a - s0) - (b - g0)) > tol * (1 + kotlin.math.abs(a - s0))) return false
    }
    return valid >= 4
}
