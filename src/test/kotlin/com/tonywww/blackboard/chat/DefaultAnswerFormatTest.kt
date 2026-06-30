package com.tonywww.blackboard.chat

import com.tonywww.blackboard.api.chat.ParsedAnswer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultAnswerFormatTest {

    @Test
    fun `parses prefix boardId and answer`() {
        assertEquals(ParsedAnswer("1", "42"), DefaultAnswerFormat.parse("!ans 1 42"))
        assertEquals(
            ParsedAnswer("north", "[[1,2],[3,4]]"),
            DefaultAnswerFormat.parse("!ANS north [[1,2],[3,4]]"),
        )
        // 内部空白保留、两端 trim。
        assertEquals(ParsedAnswer("a", "x y"), DefaultAnswerFormat.parse("  !ans  a   x y "))
    }

    @Test
    fun `rejects non-answer messages`() {
        assertNull(DefaultAnswerFormat.parse("!ans 1")) // 只有 boardId
        assertNull(DefaultAnswerFormat.parse("!answer 1 2")) // 前缀后非空白
        assertNull(DefaultAnswerFormat.parse("hello world"))
        assertNull(DefaultAnswerFormat.parse("!ans"))
    }
}
