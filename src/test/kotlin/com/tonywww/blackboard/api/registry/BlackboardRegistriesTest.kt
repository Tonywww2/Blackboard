package com.tonywww.blackboard.api.registry

import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.QuestionGenerator
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlackboardRegistriesTest {

    private fun gen(id: String, vararg tags: String): QuestionGenerator =
        QuestionGenerator.builder(ResourceLocation.tryParse(id)!!)
            .tag(*tags.map { ResourceLocation.tryParse(it)!! }.toTypedArray())
            .generate { throw UnsupportedOperationException("not used in this test") }
            .validate { _, _ -> AnswerResult.incorrect() }
            .build()

    @Test
    fun `generator pool resolves by tag, explicit and all in registration order`() {
        val reg = SimpleRegistry<QuestionGenerator>("test")
        val add = gen("blackboard:add", "blackboard:math")
        val story = gen("blackboard:story", "blackboard:text")
        reg.register(add) // convenience extension (id + tags)
        reg.register(story)

        val math = ResourceLocation.tryParse("blackboard:math")!!
        assertEquals(listOf(add), GeneratorPool.ByTag(math).resolve(reg))
        assertEquals(
            listOf(story),
            GeneratorPool.Explicit(listOf(ResourceLocation.tryParse("blackboard:story")!!)).resolve(reg),
        )
        assertEquals(listOf(add, story), GeneratorPool.All.resolve(reg))
    }

    @Test
    fun `explicit pool skips unknown ids`() {
        val reg = SimpleRegistry<QuestionGenerator>("test")
        val add = gen("blackboard:add")
        reg.register(add)
        val ids = listOf(
            ResourceLocation.tryParse("blackboard:add")!!,
            ResourceLocation.tryParse("blackboard:missing")!!,
        )
        assertEquals(listOf(add), GeneratorPool.Explicit(ids).resolve(reg))
    }

    @Test
    fun `freezeAll freezes both registries`() {
        BlackboardRegistries.freezeAll()
        assertTrue(BlackboardRegistries.QUESTION_GENERATORS.isFrozen())
        assertTrue(BlackboardRegistries.BLACKBOARD_TYPES.isFrozen())
    }
}
