package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.event.AnswerEvent
import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.content.BlackboardBlockEntity
import net.minecraft.Util
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * 服务端作答处理（internal-core-api §7 / answer-format §10 / 设计 §7.2）。
 *
 * 流程：按 `boardId` 定位黑板 → 组 [AnswerContextImpl] → 生成器 `validate` → 三态处理 → 广播
 * [BlackboardEvents.ANSWER]：
 * - `Correct`：调 [com.tonywww.blackboard.content.BlackboardBlockEntity.onSolved]（默认奖励 + 销毁，§13(3)），不再计次；
 * - `Incorrect`：`attempts++`；若 `maxAttempts>0` 且达上限则提示（§13 策略待定）；
 * - `Invalid`：不消耗作答机会，仅回送提示。
 *
 * 线程模型：聊天事件可能回调于异步线程。**定位方块实体、应用结果（发奖/销毁/计次/广播事件）均在服务器主线程**（
 * `Level.getBlockEntity` 与世界写入有主线程要求）；中间的 `generator.validate`（纯判题逻辑，可能较重如符号微积分）放到
 * [net.minecraft.Util.backgroundExecutor] 后台线程，不阻塞主线程。
 */
object AnswerHandler {

    private val logger = LoggerFactory.getLogger("Blackboard/Answer")

    /** 主线程已解析好的判题上下文（方块实体/题目/生成器/坐标均在主线程取得）。 */
    private class Prepared(
        val player: ServerPlayer,
        val level: ServerLevel,
        val be: BlackboardBlockEntity,
        val question: Question,
        val generator: QuestionGenerator,
        val pos: BlockPos,
        val state: BlockState,
    )

    fun handleAnswer(player: ServerPlayer, boardId: String, text: String) {
        val level = player.level() as? ServerLevel ?: return
        val server = level.server
        // 1) 主线程：定位黑板方块实体、取当前题与生成器。getBlockEntity 有线程校验，只能在主线程做。
        server.execute {
            val prepared = prepare(player, level, boardId) ?: return@execute
            CompletableFuture
                // 2) 后台线程：只跑纯判题逻辑（不碰世界）。
                .supplyAsync({ validate(prepared, text) }, Util.backgroundExecutor())
                // 3) 回主线程：应用结果（发奖/销毁/计次/广播事件都要写世界）。
                .thenAcceptAsync({ result -> applyResult(prepared, result) }, server)
                .exceptionally { e -> logger.error("异步判题失败 board=$boardId", e); null }
        }
    }

    /** 主线程：解析黑板/题目/生成器；缺失则回送提示并返回 null。 */
    private fun prepare(player: ServerPlayer, level: ServerLevel, boardId: String): Prepared? {
        val be = BlackboardManager.findBoard(level, boardId)
        if (be == null) {
            player.sendSystemMessage(Component.literal("No active blackboard '$boardId' here."))
            return null
        }
        val question = be.question
        if (question == null) {
            player.sendSystemMessage(Component.literal("That blackboard has no question right now."))
            return null
        }
        val generator = BlackboardRegistries.QUESTION_GENERATORS.get(question.generatorId)
        if (generator == null) {
            logger.warn("Cannot grade: unknown generator {} (board={})", question.generatorId, boardId)
            return null
        }
        return Prepared(player, level, be, question, generator, be.blockPos, be.blockState)
    }

    /** 后台线程：跑生成器判题（纯逻辑）；抛异常按 `Invalid` 兼底（不消耗机会）。 */
    private fun validate(p: Prepared, text: String): AnswerResult {
        val ctx = AnswerContextImpl(p.player, p.level, p.pos, p.state, text)
        return try {
            p.generator.validate(p.question, ctx)
        } catch (e: Throwable) {
            logger.error("validate() threw (generator=${p.question.generatorId})", e)
            AnswerResult.invalid()
        }
    }

    /** 主线程：应用判题结果——三态处理 + 广播 ANSWER 事件（均写世界）。 */
    private fun applyResult(p: Prepared, result: AnswerResult) {
        val player = p.player
        val be = p.be
        // 准备→后台判题→回主线期间，黑板可能已被破坏/被他人答对销毁；已移除则不再处理。
        if (be.isRemoved) return

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

        BlackboardEvents.ANSWER.invoke(AnswerEvent(p.level, be.blockPos, be.blockState, player, p.question, result))
    }
}
