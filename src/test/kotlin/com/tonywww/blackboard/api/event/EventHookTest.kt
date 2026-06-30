package com.tonywww.blackboard.api.event

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventHookTest {

    @Test
    fun `listeners run in registration order`() {
        val hook = EventHook<String>("test")
        val seen = mutableListOf<String>()
        hook.register { seen += "1:$it" }
        hook.register { seen += "2:$it" }
        hook.invoke("e")
        assertEquals(listOf("1:e", "2:e"), seen)
    }

    @Test
    fun `a throwing listener does not stop the others`() {
        val hook = EventHook<String>("test")
        val seen = mutableListOf<String>()
        hook.register { seen += "before" }
        hook.register { throw RuntimeException("boom") }
        hook.register { seen += "after" }
        assertDoesNotThrow { hook.invoke("e") }
        assertEquals(listOf("before", "after"), seen)
    }

    @Test
    fun `invoke with no listeners is a no-op`() {
        val hook = EventHook<String>("test")
        assertDoesNotThrow { hook.invoke("e") }
    }

    @Test
    fun `event payload is delivered to every listener`() {
        val hook = EventHook<Int>("test")
        var sum = 0
        hook.register { sum += it }
        hook.register { sum += it * 2 }
        hook.invoke(5)
        assertEquals(15, sum)
    }
}
