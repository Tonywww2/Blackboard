package com.tonywww.blackboard.api.chat

/** 聊天作答解析器。 */
fun interface AnswerFormat {
    /** 解析一条聊天消息；返回 `null` 表示「这不是一条作答消息」（放行原聊天）。 */
    fun parse(message: String): ParsedAnswer?
}
