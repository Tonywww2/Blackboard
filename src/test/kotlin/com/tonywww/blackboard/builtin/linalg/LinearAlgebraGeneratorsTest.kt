package com.tonywww.blackboard.builtin.linalg

import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.GenerationContext
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.SimpleRegistry
import com.tonywww.blackboard.api.registry.register
import com.tonywww.blackboard.api.registry.resolve
import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 装配：标签/注册/类型池解析 + Question 结构 + `Validators.number`/`Validators.matrix` 三态判题。 */
class LinearAlgebraGeneratorsTest {

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
    fun `generators are tagged linear_algebra and registrable`() {
        val reg = SimpleRegistry<QuestionGenerator>("t")
        LinearAlgebraGenerators.ALL.forEach { gen ->
            assertTrue(gen.tags.contains(BlackboardTags.LINEAR_ALGEBRA), "missing LINEAR_ALGEBRA: ${gen.id}")
            reg.register(gen)
        }
        val resolved = GeneratorPool.ByTag(BlackboardTags.LINEAR_ALGEBRA).resolve(reg)
        assertEquals(LinearAlgebraGenerators.ALL.toSet(), resolved.toSet())
    }

    @Test
    fun `type resolves to all generators`() {
        assertEquals(4, LinearAlgebraGenerators.ALL.size)
        assertEquals(
            GeneratorPool.ByTag(BlackboardTags.LINEAR_ALGEBRA),
            LinearAlgebraGenerators.LINEAR_ALGEBRA_TYPE.pool,
        )
    }

    @Test
    fun `generate produces latex content and stored answer`() {
        LinearAlgebraGenerators.ALL.forEach { gen ->
            val q = gen.generate(genCtx(3, 1))
            assertTrue(q.content.string.contains("pmatrix"), "expected pmatrix latex: ${gen.id}")
            assertTrue(q.prompt!!.string.isNotEmpty(), "empty prompt: ${gen.id}")
        }
    }

    @Test
    fun `dot product validate is three-state`() {
        val gen = LinearAlgebraGenerators.DOT
        for (diff in 0..10) for (seed in 1L..15L) {
            val q = gen.generate(genCtx(seed, diff))
            val correct = q.getDouble("answer")
            assertInstanceOf(
                AnswerResult.Correct::class.java,
                gen.validate(q, ansCtx(correct.toString())),
                "diff=$diff seed=$seed",
            )
            assertInstanceOf(AnswerResult.Incorrect::class.java, gen.validate(q, ansCtx((correct + 1).toString())))
            assertInstanceOf(AnswerResult.Invalid::class.java, gen.validate(q, ansCtx("not-a-number")))
        }
    }

    @Test
    fun `matrix generators validate is three-state`() {
        val gens = listOf(
            LinearAlgebraGenerators.EVAL,
            LinearAlgebraGenerators.MATVEC,
            LinearAlgebraGenerators.INVERSE,
        )
        for (gen in gens) for (diff in 0..10) for (seed in 1L..15L) {
            val q = gen.generate(genCtx(seed, diff))
            val correct = q.getString("answer")
            assertInstanceOf(
                AnswerResult.Correct::class.java,
                gen.validate(q, ansCtx(correct)),
                "gen=${gen.id} diff=$diff seed=$seed ans=$correct",
            )
            // 维度不符的合法矩阵 → 判错（不为 Correct）
            assertInstanceOf(
                AnswerResult.Incorrect::class.java,
                gen.validate(q, ansCtx("[[123,123,123],[123,123,123],[123,123,123]]".let { if (correct == it) "[[1]]" else it })),
                "gen=${gen.id} diff=$diff seed=$seed",
            )
            // 乱输入 → 解析失败，不消耗机会
            assertInstanceOf(AnswerResult.Invalid::class.java, gen.validate(q, ansCtx("@@@")), "gen=${gen.id}")
        }
    }
}
