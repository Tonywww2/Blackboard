package com.tonywww.blackboard.builtin.calculus

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

/** M4 · infix 解析 + LaTeX 归一化 + 求值器 + 抽样判等。 */
class CalculusParserTest {

    private fun evalOf(s: String, x: Double): Double? = CalculusEvaluator.eval(s, mapOf("x" to x))

    private fun approx(got: Double?, exp: Double, tol: Double = 1e-6) {
        assertNotNull(got, "parse/eval returned null")
        assertTrue(abs(got!! - exp) <= tol, "expected≈$exp but got $got")
    }

    @Test
    fun `parse and eval infix`() {
        approx(evalOf("2x+1", 3.0), 7.0)
        approx(evalOf("3sin(x)", PI / 2), 3.0)
        approx(evalOf("(x+1)(x-1)", 3.0), 8.0)
        approx(evalOf("-x^2", 2.0), -4.0)
        approx(evalOf("1/2*x^2", 2.0), 2.0)
        approx(evalOf("sqrt(x)", 4.0), 2.0)
        approx(evalOf("exp(x)", 0.0), 1.0)
        approx(evalOf("e^x", 0.0), 1.0)
        approx(evalOf("pi", 0.0), PI)
    }

    @Test
    fun `precedence and associativity`() {
        approx(evalOf("2+3*4", 0.0), 14.0)
        approx(evalOf("2^3^2", 0.0), 512.0) // right-assoc: 2^(3^2)=2^9
        approx(evalOf("-2^2", 0.0), -4.0) // ^ binds tighter than unary minus
    }

    @Test
    fun `implicit multiplication`() {
        approx(evalOf("2x", 5.0), 10.0)
        approx(evalOf("2(x+1)", 3.0), 8.0)
        approx(evalOf("(x+1)(x-1)", 4.0), 15.0)
    }

    @Test
    fun `latex subset normalization`() {
        approx(evalOf("\\frac{1}{2}x^2", 2.0), 2.0)
        approx(evalOf("2\\cos\\left(x\\right)", 0.0), 2.0)
        approx(evalOf("\\sqrt{x}", 9.0), 3.0)
        approx(evalOf("√(x)", 16.0), 4.0)
        approx(evalOf("x^{2}", 3.0), 9.0)
    }

    @Test
    fun `toInfix parse roundtrip`() {
        val exprs = listOf(
            Sub(Mul(Const(9.0), Pow(Var, 2.0)), Const(2.0)),
            Mul(Add(Var, Const(1.0)), Sub(Var, Const(1.0))),
            Sin(Mul(Const(2.0), Var)),
        )
        for (e in exprs) for (x in listOf(0.3, 1.1, 2.7)) {
            approx(CalculusEvaluator.eval(e.toInfix(), mapOf("x" to x)), e.eval(x))
        }
    }

    @Test
    fun `invalid input returns null`() {
        assertNull(evalOf("2x+", 1.0))
        assertNull(evalOf("sin(", 1.0))
        assertNull(evalOf("", 1.0))
        assertNull(evalOf("@#\$", 1.0))
    }

    @Test
    fun `sample equality primitives`() {
        val a = parseExpr("2*x+3")!!
        val b = parseExpr("3+2x")!!
        assertTrue(sampleEqual(a, b, 1.0, 3.0))
        assertTrue(!sampleEqual(a, parseExpr("2*x+4")!!, 1.0, 3.0))
        // +C: x^2 与 x^2+5 在两点差下相等
        assertTrue(sampleEqualUpToConst(parseExpr("x^2")!!, parseExpr("x^2+5")!!, 1.0, 3.0))
        assertTrue(!sampleEqualUpToConst(parseExpr("x^2")!!, parseExpr("x^3+5")!!, 1.0, 3.0))
    }
}
