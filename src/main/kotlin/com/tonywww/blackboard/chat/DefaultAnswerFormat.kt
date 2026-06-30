package com.tonywww.blackboard.chat

import com.tonywww.blackboard.api.chat.AnswerFormat
import com.tonywww.blackboard.api.chat.ParsedAnswer

/**
 * 默认作答格式：`!ans <boardId> <答案...>`。
 *  - 前缀 `!ans`（大小写不敏感），前缀后必须是空白或字符串结束（避免误吞 `!answer` 等）；
 *  - boardId：前缀后首个空白分隔的 token；
 *  - 答案正文：boardId 之后的全部剩余文本，仅两端 `trim`，内部空白原样保留（矩阵/含空格答案需要）。
 *
 * 见 answer-format §2；最终形态待 design §13(1) 确认。
 */
object DefaultAnswerFormat : AnswerFormat {
    private const val PREFIX = "!ans"

    override fun parse(message: String): ParsedAnswer? {
        val msg = message.trimStart()
        if (!msg.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return null

        // 前缀后必须是空白或结束。
        val afterPrefix = msg.substring(PREFIX.length)
        if (afterPrefix.isNotEmpty() && !afterPrefix[0].isWhitespace()) return null

        val rest = afterPrefix.trimStart()
        if (rest.isEmpty()) return null
        val firstWs = rest.indexOfFirst { it.isWhitespace() }
        if (firstWs < 0) return null // 只有 boardId，没有答案
        val boardId = rest.substring(0, firstWs)
        val answer = rest.substring(firstWs + 1).trim()
        if (boardId.isEmpty() || answer.isEmpty()) return null
        return ParsedAnswer(boardId, answer)
    }
}
