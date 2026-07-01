package com.tonywww.blackboard.builtin

import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.GenerationContext
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.SimpleRegistry
import com.tonywww.blackboard.api.registry.register
import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuiltinGeneratorsTest {

    /** 生成器只用到 [GenerationContext.random]，其余成员在单测中不被访问。 */
    private fun genCtx(seed: Long): GenerationContext = object : GenerationContext {
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val blackboard get() = throw UnsupportedOperationException()
        override val random: RandomSource = RandomSource.create(seed)
        override val player get() = null
        override val difficulty = 0
    }

    private fun ansCtx(answer: String): AnswerContext = object : AnswerContext {
        override val player get() = throw UnsupportedOperationException()
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val text = answer
    }

    @Test
    fun `math generators accept generated answer and distinguish wrong vs invalid`() {
        val math = listOf(
            BuiltinGenerators.ADDITION,
            BuiltinGenerators.SUBTRACTION,
            BuiltinGenerators.MULTIPLICATION,
            BuiltinGenerators.DIVISION,
            BuiltinGenerators.SQUARE,
        )
        for ((i, gen) in math.withIndex()) {
            val q = gen.generate(genCtx(100L + i))
            val answer = q.getInt("answer")
            assertTrue(gen.validate(q, ansCtx(answer.toString())) is AnswerResult.Correct, "${gen.id} accepts $answer")
            assertTrue(gen.validate(q, ansCtx((answer + 1).toString())) is AnswerResult.Incorrect, "${gen.id} rejects ${answer + 1}")
            assertTrue(gen.validate(q, ansCtx("not a number")) is AnswerResult.Invalid, "${gen.id} marks junk invalid")
        }
    }

    @Test
    fun `text generator judges yes-no`() {
        val gen = BuiltinGenerators.TRUE_FALSE_SUM
        val q = gen.generate(genCtx(7))
        val correct = q.getString("answer")
        val wrong = if (correct == "yes") "no" else "yes"
        assertTrue(gen.validate(q, ansCtx(correct)) is AnswerResult.Correct)
        assertTrue(gen.validate(q, ansCtx(wrong)) is AnswerResult.Incorrect)
    }

    @Test
    fun `generators are tagged and registrable`() {
        assertTrue(BuiltinGenerators.ADDITION.tags.contains(BlackboardTags.MATH))
        assertTrue(BuiltinGenerators.TRUE_FALSE_SUM.tags.contains(BlackboardTags.TEXT))
        // 每个内置生成器都带 DEFAULT，默认黑板（ByTag(DEFAULT)）才能选到题。
        assertTrue(BuiltinGenerators.ALL.all { it.tags.contains(BlackboardTags.DEFAULT) })

        val reg = SimpleRegistry<QuestionGenerator>("test")
        BuiltinGenerators.ALL.forEach { reg.register(it) }
        assertEquals(BuiltinGenerators.ALL.size, reg.all().size)
        assertEquals(BuiltinGenerators.ALL.size, reg.byTag(BlackboardTags.DEFAULT).size)
        assertSame(BuiltinGenerators.ADDITION, reg.get(BuiltinGenerators.ADDITION.id))
    }
}
