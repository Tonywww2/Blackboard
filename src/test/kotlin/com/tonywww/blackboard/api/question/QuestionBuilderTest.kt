package com.tonywww.blackboard.api.question

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuestionBuilderTest {

    @Test
    fun `builder stores and reads data round-trip`() {
        val rid = ResourceLocation.tryParse("blackboard:test")!!
        val q = Questions.builder(rid)
            .content(Component.literal("1 + 1 = ?"))
            .store("answer", 2)
            .store("ratio", 3.5)
            .store("label", "two")
            .store("solved", true)
            .build()

        assertEquals(rid, q.generatorId)
        assertEquals(2, q.getInt("answer"))
        assertEquals(3.5, q.getDouble("ratio"))
        assertEquals("two", q.getString("label"))
        assertTrue(q.getBoolean("solved"))
    }

    @Test
    fun `missing content fails fast`() {
        val rid = ResourceLocation.tryParse("blackboard:test")!!
        val ex = runCatching { Questions.builder(rid).build() }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
