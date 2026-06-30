package com.tonywww.blackboard.api.registry

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleRegistryTest {

    private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath("blackboard", path)

    @Test
    fun `register then get returns the value`() {
        val reg = SimpleRegistry<String>("t")
        reg.register(id("a"), "A")
        assertEquals("A", reg.get(id("a")))
        assertEquals(id("a"), reg.idOf("A"))
        assertTrue(reg.contains(id("a")))
    }

    @Test
    fun `duplicate id throws and does not overwrite`() {
        val reg = SimpleRegistry<String>("t")
        reg.register(id("a"), "A")
        assertThrows(IllegalArgumentException::class.java) {
            reg.register(id("a"), "B")
        }
        assertEquals("A", reg.get(id("a")))
    }

    @Test
    fun `iteration order follows registration order`() {
        val reg = SimpleRegistry<String>("t")
        reg.register(id("c"), "C")
        reg.register(id("a"), "A")
        reg.register(id("b"), "B")
        assertEquals(listOf("C", "A", "B"), reg.all())
        assertEquals(listOf(id("c"), id("a"), id("b")), reg.ids())
    }

    @Test
    fun `byTag returns tagged values in registration order and empty for unknown tag`() {
        val reg = SimpleRegistry<String>("t")
        reg.register(id("a"), "A", setOf(id("math")))
        reg.register(id("b"), "B", setOf(id("text")))
        reg.register(id("c"), "C", setOf(id("math"), id("text")))
        assertEquals(listOf("A", "C"), reg.byTag(id("math")))
        assertEquals(listOf("B", "C"), reg.byTag(id("text")))
        assertTrue(reg.byTag(id("unknown")).isEmpty())
    }

    @Test
    fun `register after freeze throws`() {
        val reg = SimpleRegistry<String>("t")
        reg.register(id("a"), "A")
        assertFalse(reg.isFrozen())
        reg.freeze()
        assertTrue(reg.isFrozen())
        assertThrows(IllegalStateException::class.java) {
            reg.register(id("b"), "B")
        }
    }

    @Test
    fun `idOf returns null for an unregistered value`() {
        val reg = SimpleRegistry<String>("t")
        assertNull(reg.idOf("missing"))
    }
}
