package com.tonywww.blackboard.builtin.calculus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs

/** M1 · 表达式 AST：求值 / 求导（有限差分核对）/ 化简 / 渲染。 */
class CalculusExprTest {

    private fun approx(got: Double, exp: Double, tol: Double = 1e-4) =
        assertTrue(abs(got - exp) <= tol, "expected≈$exp but got $got")

    /** 中心差分数值导数，用于独立核对符号求导。 */
    private fun fd(f: Expr, x: Double, h: Double = 1e-6) = (f.eval(x + h) - f.eval(x - h)) / (2 * h)

    private fun checkDeriv(f: Expr, x: Double) = approx(f.derivative().eval(x), fd(f, x))

    @Test
    fun `eval basic nodes`() {
        approx(Pow(Var, 3.0).eval(2.0), 8.0)
        approx(Sqrt(Const(4.0)).eval(0.0), 2.0)
        approx(Ln(Const(E)).eval(0.0), 1.0)
        approx(Sin(Const(PI / 2)).eval(0.0), 1.0)
        approx(Add(Mul(Const(2.0), Var), Const(1.0)).eval(3.0), 7.0)
    }

    @Test
    fun `derivative rules match finite difference`() {
        val x = 1.3
        checkDeriv(Pow(Var, 3.0), x)
        checkDeriv(Sin(Var), x)
        checkDeriv(Cos(Var), x)
        checkDeriv(Exp(Var), x)
        checkDeriv(Ln(Var), x) // x>0
        checkDeriv(Sqrt(Var), x) // x>0
        checkDeriv(Mul(Var, Sin(Var)), x) // 乘积
        checkDeriv(Div(Var, Add(Pow(Var, 2.0), Const(1.0))), x) // 商（恒正分母）
        checkDeriv(Sin(Mul(Const(2.0), Var)), x) // 链式
    }

    @Test
    fun `derivative exact points`() {
        approx(Pow(Var, 3.0).derivative().eval(2.0), 12.0) // 3x^2 @2
        approx(Sin(Var).derivative().eval(0.0), 1.0) // cos0
    }

    @Test
    fun `simplify identities and idempotence`() {
        assertEquals(Var, Add(Var, ZERO).simplify())
        assertEquals(Var, Mul(Var, ONE).simplify())
        assertEquals(ZERO, Mul(Var, ZERO).simplify())
        assertEquals(ONE, Pow(Var, 0.0).simplify())
        assertEquals(Var, Pow(Var, 1.0).simplify())
        assertEquals(Var, Neg(Neg(Var)).simplify())
        assertEquals(Const(5.0), Add(Const(2.0), Const(3.0)).simplify())

        val e = Add(Mul(Const(2.0), Var), Sub(Var, ZERO))
        assertEquals(e.simplify(), e.simplify().simplify())
    }

    @Test
    fun `toInfix compact`() {
        assertEquals("2*x+1", Add(Mul(Const(2.0), Var), Const(1.0)).toInfix())
        assertEquals("9*x^2-2", Sub(Mul(Const(9.0), Pow(Var, 2.0)), Const(2.0)).toInfix())
        assertEquals("(x+1)*(x-1)", Mul(Add(Var, Const(1.0)), Sub(Var, Const(1.0))).toInfix())
    }

    @Test
    fun `toLatex snapshots`() {
        assertEquals("\\frac{1}{2}", Div(Const(1.0), Const(2.0)).toLatex())
        assertEquals("x^{2}", Pow(Var, 2.0).toLatex())
        assertEquals("\\sqrt{x}", Sqrt(Var).toLatex())
        assertEquals("\\sin\\left(x\\right)", Sin(Var).toLatex())
        assertEquals("\\frac{d}{dx}\\left(x^{2}\\right)", diffLatex(Pow(Var, 2.0)))
    }
}
