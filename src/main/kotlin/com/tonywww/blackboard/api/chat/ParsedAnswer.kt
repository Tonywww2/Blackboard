package com.tonywww.blackboard.api.chat

/**
 * 解析后的作答：黑板标识 + 答案正文。
 *
 * @property boardId 黑板标识（解析策略见 answer-format §3，最终形态待 design §13 确认）。
 * @property answer 去除前缀与 boardId 后的答案正文（保留内部空格，仅 trim 两端）。
 */
data class ParsedAnswer(
    val boardId: String,
    val answer: String,
)
