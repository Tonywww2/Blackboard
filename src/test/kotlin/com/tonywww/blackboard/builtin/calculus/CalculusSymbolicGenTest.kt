package com.tonywww.blackboard.builtin.calculus

import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.GenerationContext
import com.tonywww.blackboard.api.question.QuestionGenerator
import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** M6 · 符号装配：`EXPRESSION` 判题三态（不同书写/+C 接受、乱输入→Invalid）与 `Question.data` 键齐全。 */
class CalculusSymbolicGenTest {

    private fun genCtx(seed: Long, diff: Int) = object : GenerationContext {
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val blackboard get() = throw UnsupportedOperationException()
        override val random: RandomSource = RandomSource.create(seed)
        override val player = null
        override val difficulty = diff
    }

    private fun ansCtx(answer: String) = object : AnswerContext {
        override val player get() = throw UnsupportedOperationException()
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val text = answer
    }

    @Test
    fun `differentiation validate is three-state and accepts equivalent forms`() {
        val gen = CalculusGenerators.DIFFERENTIATION
        for (seed in 1L..15L) {
            val q = gen.generate(genCtx(seed, 2))
            val ans = q.getString("answer")

            // 原样正确
            assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx(ans)), "seed=$seed ans=$ans")
            // 不同书写（把 * 换成 LaTeX \cdot）仍等价
            val latexVariant = ans.replace("*", " \\cdot ")
            assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx(latexVariant)), "seed=$seed")
            // 加常数 1 → 求导题不等价
            assertInstanceOf(AnswerResult.Incorrect::class.java, gen.validate(q, ansCtx("$ans+1")), "seed=$seed")
            // 乱输入 → 解析失败，不消耗机会
            assertInstanceOf(AnswerResult.Invalid::class.java, gen.validate(q, ansCtx("@@@")), "seed=$seed")
        }
    }

    @Test
    fun `indefinite integral accepts plus constant and rejects non-constant diff`() {
        val gen = CalculusGenerators.INDEF_INTEGRAL
        for (seed in 1L..15L) {
            val q = gen.generate(genCtx(seed, 3))
            assertTrue(q.getBoolean("intg"))
            val ans = q.getString("answer")

            assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx(ans)), "seed=$seed ans=$ans")
            // +C 被接受
            assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx("$ans+7")), "seed=$seed")
            // 加 x 项属非常数差 → 不等价
            assertInstanceOf(AnswerResult.Incorrect::class.java, gen.validate(q, ansCtx("$ans+x")), "seed=$seed")
            // 乱输入 → Invalid
            assertInstanceOf(AnswerResult.Invalid::class.java, gen.validate(q, ansCtx("###")), "seed=$seed")
        }
    }

    @Test
    fun `symbolic questions store required data keys`() {
        val cases = listOf(CalculusGenerators.DIFFERENTIATION to false, CalculusGenerators.INDEF_INTEGRAL to true)
        for ((gen, isIntegral) in cases) {
            val q: com.tonywww.blackboard.api.question.Question = gen.generate(genCtx(4, 2))
            assertTrue(q.data.contains("answer"), "${gen.id} 缺 answer")
            assertTrue(q.data.contains("lo") && q.data.contains("hi"), "${gen.id} 缺 lo/hi")
            assertTrue(q.data.contains("intg"), "${gen.id} 缺 intg")
            assertTrue(q.getString("answer").isNotEmpty(), "${gen.id} answer 为空")
            assertTrue(q.getDouble("lo") < q.getDouble("hi"), "${gen.id} lo<hi 不成立")
            if (isIntegral) assertTrue(q.getBoolean("intg")) else assertFalse(q.getBoolean("intg"))
        }
    }

    @Test
    fun `symbolic generators are present in ALL`() {
        val all: List<QuestionGenerator> = CalculusGenerators.ALL
        assertTrue(all.contains(CalculusGenerators.DIFFERENTIATION))
        assertTrue(all.contains(CalculusGenerators.INDEF_INTEGRAL))
    }
}
