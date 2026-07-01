package com.tonywww.blackboard.builtin.calculus

import com.tonywww.blackboard.api.BlackboardApi
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

/** M3 · 装配：标签/注册/Question 结构/`Validators.number` 三态/类型池解析。 */
class CalculusGeneratorsTest {

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
    fun `generators are tagged calculus and registrable`() {
        val reg = SimpleRegistry<QuestionGenerator>("t")
        CalculusGenerators.ALL.forEach { gen ->
            assertTrue(gen.tags.contains(BlackboardTags.CALCULUS), "missing CALCULUS: ${gen.id}")
            reg.register(gen)
            assertTrue(reg.contains(gen.id))
        }
        // ByTag(CALCULUS) 能解析到全部微积分生成器
        val resolved = GeneratorPool.ByTag(BlackboardTags.CALCULUS).resolve(reg)
        assertEquals(CalculusGenerators.ALL.toSet(), resolved.toSet())
    }

    @Test
    fun `generate produces a question with latex content and numeric answer`() {
        CalculusGenerators.ALL.forEach { gen ->
            val q = gen.generate(genCtx(3, 0))
            assertTrue(q.content.string.isNotEmpty(), "empty content: ${gen.id}")
            assertTrue(q.prompt!!.string.isNotEmpty(), "empty prompt: ${gen.id}")
            // 数值答案存在（getDouble 不抛）
            q.getDouble("answer")
        }
    }

    @Test
    fun `validate is three-state`() {
        val gen = CalculusGenerators.DERIV_AT_POINT
        val q = gen.generate(genCtx(5, 0))
        val correct = q.getDouble("answer").toLong().toString()

        assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx(correct)))
        assertInstanceOf(AnswerResult.Incorrect::class.java, gen.validate(q, ansCtx((q.getDouble("answer") + 123.0).toString())))
        assertInstanceOf(AnswerResult.Invalid::class.java, gen.validate(q, ansCtx("not a number")))
    }

    @Test
    fun `calculus type id and pool`() {
        assertEquals(BlackboardApi.id("calculus"), CalculusGenerators.CALCULUS_TYPE.id)
        assertEquals(GeneratorPool.ByTag(BlackboardTags.CALCULUS), CalculusGenerators.CALCULUS_TYPE.pool)
    }
}
