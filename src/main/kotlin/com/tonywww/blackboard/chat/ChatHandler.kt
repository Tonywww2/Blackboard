package com.tonywww.blackboard.chat

import com.tonywww.blackboard.api.chat.AnswerFormat
import com.tonywww.blackboard.core.AnswerHandler
import net.minecraft.server.level.ServerPlayer

/**
 * 服务端聊天作答路由（设计 §7 / answer-format §2）。
 *
 * 平台层（P7-A 的聊天事件订阅，C4）调用 [handle] 传入玩家与原始聊天文本；本类用 [format] 解析，
 * 命中作答则路由到 [AnswerHandler]。
 *
 * §13(2)：当前**不拦截**原聊天——调用方可据返回值（是否命中作答）自行决定要不要取消事件。
 */
object ChatHandler {

    /** 解析作答消息的格式；默认 [DefaultAnswerFormat]，可替换为自定义实现。 */
    var format: AnswerFormat = DefaultAnswerFormat

    /**
     * 处理一条服务端聊天消息。
     *
     * @return `true` 表示这是一条作答消息（已路由到 [AnswerHandler]）；`false` 表示非作答，应放行原聊天。
     */
    fun handle(player: ServerPlayer, message: String): Boolean {
        val parsed = format.parse(message) ?: return false
        AnswerHandler.handleAnswer(player, parsed.boardId, parsed.answer)
        return true
    }
}
