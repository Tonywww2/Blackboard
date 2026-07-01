package com.tonywww.blackboard.builtin.logic

import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.GenerationContext
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.SimpleRegistry
import com.tonywww.blackboard.api.registry.register
import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogicGeneratorsTest {

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
    fun `generators tagged logic and registrable`() {
        val reg = SimpleRegistry<QuestionGenerator>("t")
        LogicGenerators.ALL.forEach { gen ->
            assertTrue(gen.tags.contains(BlackboardTags.LOGIC), "missing LOGIC: ${gen.id}")
            reg.register(gen)
            assertTrue(reg.contains(gen.id))
        }
    }

    @Test
    fun `generate is three-state and lenient across all difficulties`() {
        for (gen in LogicGenerators.ALL) {
            for (diff in 0..10) {
                for (seed in 1L..20L) {
                    val q = gen.generate(genCtx(seed, diff))
                    assertTrue(q.content.string.isNotEmpty(), "empty content: ${gen.id} diff=$diff seed=$seed")
                    val correct = q.getString("answer") // "true" / "false"

                    // 存储答案原样被接受（自洽）
                    assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx(correct)), "gen=${gen.id} diff=$diff seed=$seed")
                    // 宽松写法（T/F）同样接受
                    assertInstanceOf(AnswerResult.Correct::class.java, gen.validate(q, ansCtx(if (correct == "true") "T" else "F")), "gen=${gen.id} lenient")
                    // 相反答案 → 错
                    assertInstanceOf(AnswerResult.Incorrect::class.java, gen.validate(q, ansCtx(if (correct == "true") "false" else "true")), "gen=${gen.id} wrong")
                    // 乱输入 → Invalid（不消耗次数）
                    assertInstanceOf(AnswerResult.Invalid::class.java, gen.validate(q, ansCtx("@@@")), "gen=${gen.id} junk")
                }
            }
        }
    }
}
