package com.tonywww.blackboard.builtin.calculus

import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** M2 · 数值题型自洽性（求导/定积分/极限）。用固定种子遍历多样本，独立核算答案。 */
class CalculusProblemsTest {

    private fun r(seed: Long) = RandomSource.create(seed)

    /** 中心差分数值导数。 */
    private fun fd(f: Expr, x: Double, h: Double = 1e-6) = (f.eval(x + h) - f.eval(x - h)) / (2 * h)

    /** 复合 Simpson 数值积分（对 ≤3 次多项式精确）。 */
    private fun simpson(f: Expr, a: Double, b: Double, n: Int = 1000): Double {
        val h = (b - a) / n
        var s = f.eval(a) + f.eval(b)
        for (i in 1 until n) s += (if (i % 2 == 0) 2 else 4) * f.eval(a + i * h)
        return s * h / 3
    }

    @Test
    fun `same seed is deterministic`() {
        assertEquals(CalculusProblems.derivativeAtPoint(r(7), 0), CalculusProblems.derivativeAtPoint(r(7), 0))
        assertEquals(CalculusProblems.definiteIntegral(r(9), 0), CalculusProblems.definiteIntegral(r(9), 0))
        assertEquals(CalculusProblems.limit(r(11), 0), CalculusProblems.limit(r(11), 0))
    }

    @Test
    fun `derivative-at-point answer matches finite difference`() {
        for (s in 1L..40L) {
            val p = CalculusProblems.derivativeAtPoint(r(s), 0)
            assertEquals(AnswerKind.NUMBER, p.answerKind)
            assertTrue(abs(fd(p.fn!!, p.at!!) - p.answerNumber!!) <= 1e-3, "seed=$s ans=${p.answerNumber}")
        }
    }

    @Test
    fun `definite integral answer matches numeric integration`() {
        for (s in 1L..40L) {
            val p = CalculusProblems.definiteIntegral(r(s), 0)
            val num = simpson(p.fn!!, p.intA!!, p.intB!!)
            assertTrue(abs(num - p.answerNumber!!) <= 1e-3, "seed=$s ans=${p.answerNumber} simpson=$num")
        }
    }

    @Test
    fun `limit answer matches numeric probe`() {
        for (s in 1L..60L) {
            val p = CalculusProblems.limit(r(s), 0)
            val at = p.at!!
            val probe = if (at.isFinite()) at + 1e-5 else 1e7
            val got = p.fn!!.eval(probe)
            assertTrue(abs(got - p.answerNumber!!) <= 1e-2, "seed=$s ans=${p.answerNumber} probe=$got")
        }
    }

    @Test
    fun `generated polynomials are finite on grid`() {
        for (s in 1L..20L) {
            val p = CalculusProblems.derivativeAtPoint(r(s), 0)
            var x = -3.0
            while (x <= 3.0) {
                assertTrue(p.fn!!.eval(x).isFinite(), "seed=$s x=$x")
                x += 0.5
            }
        }
    }
}
