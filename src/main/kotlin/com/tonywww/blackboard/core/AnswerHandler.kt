package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.event.AnswerEvent
import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

/**
 * 服务端作答处理（internal-core-api §7 / answer-format §10 / 设计 §7.2）。
 *
 * 流程：按 `boardId` 定位黑板 → 组 [AnswerContextImpl] → 生成器 `validate` → 三态处理 → 广播
 * [BlackboardEvents.ANSWER]：
 * - `Correct`：调 [com.tonywww.blackboard.content.BlackboardBlockEntity.onSolved]（默认奖励 + 销毁，§13(3)），不再计次；
 * - `Incorrect`：`attempts++`；若 `maxAttempts>0` 且达上限则提示（§13 策略待定）；
 * - `Invalid`：不消耗作答机会，仅回送提示。
 *
 * 全部在服务端主线程调用（§11）。
 */
object AnswerHandler {

    private val logger = LoggerFactory.getLogger("Blackboard/Answer")

    fun handleAnswer(player: ServerPlayer, boardId: String, text: String) {
        val level = player.level() as? ServerLevel ?: return

        val be = BlackboardManager.findBoard(level, boardId)
        if (be == null) {
            player.sendSystemMessage(Component.literal("No active blackboard '$boardId' here."))
            return
        }
        val question = be.question
        if (question == null) {
            player.sendSystemMessage(Component.literal("That blackboard has no question right now."))
            return
        }
        val generator = BlackboardRegistries.QUESTION_GENERATORS.get(question.generatorId)
        if (generator == null) {
            logger.warn("Cannot grade: unknown generator {} (board={})", question.generatorId, boardId)
            return
        }

        val ctx = AnswerContextImpl(player, level, be.blockPos, be.blockState, text)
        val result = try {
            generator.validate(question, ctx)
        } catch (e: Throwable) {
            logger.error("validate() threw (board=$boardId, generator=${question.generatorId})", e)
            return
        }

        when (result) {
            is AnswerResult.Correct -> {
                result.feedback?.let { player.sendSystemMessage(it) }
                be.onSolved(player, result) // reward + destroy (overridable by subclasses)
            }

            is AnswerResult.Incorrect -> {
                val attempts = be.incrementAttempts()
                player.sendSystemMessage(result.feedback ?: Component.literal("Incorrect."))
                val max = be.blackboardType?.maxAttempts ?: 0
                if (max > 0 && attempts >= max) {
                    // TODO(§13): out-of-attempts strategy (lock / regenerate) undecided; just notify for now.
                    player.sendSystemMessage(Component.literal("No attempts left for this question."))
                }
            }

            is AnswerResult.Invalid -> {
                // Does not consume an attempt; only echo a format/parse hint.
                player.sendSystemMessage(result.feedback ?: Component.literal("Couldn't read that answer; check the format."))
            }
        }

        BlackboardEvents.ANSWER.invoke(AnswerEvent(level, be.blockPos, be.blockState, player, question, result))
    }
}
