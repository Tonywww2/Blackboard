package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.api.question.Questions
import com.tonywww.blackboard.platform.PlatformComponents
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class QuestionNbtTest {

    // Plain text components need no registry entries; an empty provider is enough and avoids any
    // Minecraft bootstrap (vanilla Bootstrap.bootStrap() is unreliable in the Forge unit-test env).
    private val registries = RegistryAccess.EMPTY

    private companion object {
        // The 1.20.1 serializer is registry-free and works here; the 1.21.x codec needs a
        // bootstrapped registry that isn't reliably available in this unit-test env. Probe once and
        // skip (not fail) the serialization round-trip tests where it is unavailable. QuestionNbt's
        // own logic is loader-independent and is fully exercised on 1.20.1.
        val componentsAvailable: Boolean = try {
            PlatformComponents.toJson(Component.literal("probe"), RegistryAccess.EMPTY)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath("blackboard", path)

    private fun sampleQuestion(): Question = Questions.builder(id("add"))
        .content(Component.literal("1 + 1 = ?"))
        .prompt(Component.literal("Compute 1 + 1"))
        .store("answer", 2)
        .store("text", "two")
        .build()

    @Test
    fun `full round-trip preserves generator, content, prompt and data`() {
        assumeTrue(componentsAvailable)
        val q = sampleQuestion()
        val back = questionFromNbt(q.toNbt(registries), registries)
        assertNotNull(back)
        back!!
        assertEquals(q.generatorId, back.generatorId)
        assertEquals("1 + 1 = ?", back.content.string)
        assertEquals("Compute 1 + 1", back.prompt?.string)
        assertEquals(2, back.getInt("answer"))
        assertEquals("two", back.getString("text"))
    }

    @Test
    fun `full nbt contains all four keys`() {
        assumeTrue(componentsAvailable)
        val tag = sampleQuestion().toNbt(registries)
        assertTrue(tag.contains("Generator"))
        assertTrue(tag.contains("Content"))
        assertTrue(tag.contains("Prompt"))
        assertTrue(tag.contains("Data"))
    }

    @Test
    fun `client nbt omits Data and Prompt and still deserializes`() {
        assumeTrue(componentsAvailable)
        val q = sampleQuestion()
        val tag = q.toClientNbt(registries)
        assertTrue(tag.contains("Generator"))
        assertTrue(tag.contains("Content"))
        assertFalse(tag.contains("Data"))
        assertFalse(tag.contains("Prompt"))

        val back = questionFromNbt(tag, registries)
        assertNotNull(back)
        back!!
        assertEquals(q.generatorId, back.generatorId)
        assertEquals("1 + 1 = ?", back.content.string)
        assertTrue(back.data.isEmpty)
        assertNull(back.prompt)
    }

    @Test
    fun `question without prompt round-trips with null prompt`() {
        assumeTrue(componentsAvailable)
        val q = Questions.builder(id("noprompt"))
            .content(Component.literal("Q"))
            .store("a", 1)
            .build()
        val back = questionFromNbt(q.toNbt(registries), registries)
        assertNotNull(back)
        assertNull(back!!.prompt)
        assertEquals(1, back.getInt("a"))
    }

    @Test
    fun `fromNbt returns null when required keys are missing`() {
        assertNull(questionFromNbt(CompoundTag(), registries))
        val onlyGenerator = CompoundTag().apply { putString("Generator", "blackboard:add") }
        assertNull(questionFromNbt(onlyGenerator, registries))
    }

    @Test
    fun `fromNbt returns null on an unparseable generator id`() {
        val tag = CompoundTag().apply {
            putString("Generator", "Not A Valid Id!!")
            putString("Content", "irrelevant")
        }
        assertNull(questionFromNbt(tag, registries))
    }
}
