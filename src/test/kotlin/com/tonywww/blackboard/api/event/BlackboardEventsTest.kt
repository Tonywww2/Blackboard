package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.board.SelectionContext
import com.tonywww.blackboard.api.board.WeightedGenerator
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.QuestionGenerator
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BlackboardEventsTest {

    private fun gen(id: String): QuestionGenerator =
        QuestionGenerator.builder(ResourceLocation.tryParse(id)!!)
            .generate { throw UnsupportedOperationException("not used in this test") }
            .validate { _, _ -> AnswerResult.incorrect() }
            .build()

    /** 选题事件只用到 candidates/forced，SelectionContext 成员在单测中不被访问。 */
    private fun selectionContext(): SelectionContext = object : SelectionContext {
        override val blackboard get() = throw UnsupportedOperationException()
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val player get() = null
    }

    @Test
    fun `select event candidates are mutable and forced defaults null`() {
        val first = gen("blackboard:add")
        val event = SelectGeneratorEvent(selectionContext(), mutableListOf(WeightedGenerator(first, 5)))

        assertNull(event.forced)
        assertEquals(1, event.candidates.size)

        event.candidates.add(WeightedGenerator(gen("blackboard:sub"), 3))
        event.candidates[0].weight = 10
        event.forced = first

        assertEquals(2, event.candidates.size)
        assertEquals(10, event.candidates[0].weight)
        assertSame(first, event.forced)
    }

    @Test
    fun `aggregator exposes four distinct hooks`() {
        val hooks = setOf(
            BlackboardEvents.SELECT_GENERATOR,
            BlackboardEvents.QUESTION_GENERATED,
            BlackboardEvents.ANSWER,
            BlackboardEvents.REWARD,
        )
        assertEquals(4, hooks.size)
    }

    @Test
    fun `select hook dispatches the event to listeners`() {
        var seen: SelectGeneratorEvent? = null
        BlackboardEvents.SELECT_GENERATOR.register { seen = it }

        val event = SelectGeneratorEvent(selectionContext(), mutableListOf())
        BlackboardEvents.SELECT_GENERATOR.invoke(event)

        assertSame(event, seen)
    }
}
