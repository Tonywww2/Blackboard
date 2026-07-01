package com.tonywww.blackboard.builtin.calculus

import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** M5 · 符号题型（求导 A–F / 不定积分 H）自洽性验证。 */
class CalculusSymbolicTest {

    private fun r(seed: Long) = RandomSource.create(seed)

    @Test
    fun `differentiation answer equals derivative by sampling`() {
        for (d in 0..3) for (s in 1L..25L) {
            val p = CalculusProblems.differentiation(r(s * 10 + d), d)
            assertEquals(AnswerKind.EXPRESSION, p.answerKind)
            val ans = parseExpr(p.answerInfix!!)
            assertNotNull(ans, "答案无法解析: ${p.answerInfix}")
            assertTrue(
                sampleEqual(ans!!, p.fn!!.derivative(), p.sampleLo, p.sampleHi),
                "seed=$s d=$d ans=${p.answerInfix}",
            )
        }
    }

    @Test
    fun `indefinite integral answer differentiates back to integrand`() {
        for (s in 1L..40L) {
            val p = CalculusProblems.indefiniteIntegral(r(s), 3)
            assertTrue(p.integral)
            assertEquals(AnswerKind.EXPRESSION, p.answerKind)
            val bigF = parseExpr(p.answerInfix!!)
            assertNotNull(bigF, "答案无法解析: ${p.answerInfix}")
            assertTrue(
                sampleEqual(bigF!!.derivative(), p.fn!!, p.sampleLo, p.sampleHi),
                "seed=$s F=${p.answerInfix}",
            )
        }
    }

    @Test
    fun `integral answer accepts plus constant`() {
        for (s in 1L..20L) {
            val p = CalculusProblems.indefiniteIntegral(r(s), 3)
            val std = parseExpr(p.answerInfix!!)!!
            val plusC = parseExpr("${p.answerInfix}+5")!!
            assertTrue(sampleEqualUpToConst(std, plusC, p.sampleLo, p.sampleHi), "seed=$s")
        }
    }

    @Test
    fun `symbolic functions are finite on interval`() {
        for (s in 1L..30L) {
            val p = CalculusProblems.differentiation(r(s), 2)
            var x = 1.0
            while (x <= 3.0) {
                assertTrue(p.fn!!.eval(x).isFinite(), "seed=$s x=$x 被求导函数不可求值")
                x += 0.25
            }
        }
    }
}
