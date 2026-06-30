package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.event.QuestionGeneratedEvent
import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.content.BlackboardBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 服务端黑板管理器：
 * - 维护每个维度的 `boardId → BlockPos` 索引（由平台层在区块加载/卸载、放置/破坏时调用 [track]/[untrack]）；
 * - 驱动出题 [generateQuestion]（选题 → 出题 → 广播 [QuestionGeneratedEvent] → 写入方块实体并标脏同步）；
 * - 协调注册表冻结时机 [freezeRegistries]（internal-core-api §10）。
 *
 * 约定（§11）：所有方法在服务端主线程调用；§13(5) 每块黑板固定一题、全员共享。
 */
object BlackboardManager {

    private val logger = LoggerFactory.getLogger("Blackboard/Manager")

    /** 每个维度一张 `boardId → 方块坐标` 表。 */
    private val boards = ConcurrentHashMap<ResourceKey<Level>, MutableMap<String, BlockPos>>()

    /** 登记一块黑板（区块加载 / 放置时）。 */
    fun track(level: ServerLevel, be: BlackboardBlockEntity) {
        if (be.boardId.isEmpty()) return
        boards.computeIfAbsent(level.dimension()) { ConcurrentHashMap() }[be.boardId] = be.blockPos
    }

    /** 注销一块黑板（区块卸载 / 破坏时）。 */
    fun untrack(level: ServerLevel, be: BlackboardBlockEntity) {
        if (be.boardId.isEmpty()) return
        boards[level.dimension()]?.remove(be.boardId)
    }

    /** 按 [boardId] 在 [level] 中定位活跃的黑板方块实体；索引过期或不匹配返回 `null`。 */
    fun findBoard(level: ServerLevel, boardId: String): BlackboardBlockEntity? {
        val pos = boards[level.dimension()]?.get(boardId) ?: return null
        val be = level.getBlockEntity(pos) as? BlackboardBlockEntity ?: return null
        return if (be.boardId == boardId) be else null
    }

    /**
     * 为 [be] 出一道新题（服务端）：选题 → 出题 → 广播 [QuestionGeneratedEvent] →
     * [BlackboardBlockEntity.setQuestion]（写入并标脏同步）。
     *
     * 返回题目；若不在服务端、未设黑板类型，或选题/出题（第三方生成器）抛错，则返回 `null`。
     */
    fun generateQuestion(be: BlackboardBlockEntity, player: ServerPlayer? = null): Question? {
        val level = be.level as? ServerLevel ?: return null
        val type = be.blackboardType ?: return null
        val pos = be.blockPos
        val state = be.blockState
        val question = try {
            val selCtx = SelectionContextImpl(type, level, pos, state, player)
            val generator = selectGenerator(type, selCtx)
            // §13(4): 难度来源未定，暂用 0。
            val genCtx = GenerationContextImpl(level, pos, state, type, level.random, player, 0)
            generator.generate(genCtx)
        } catch (e: Throwable) {
            logger.error("出题失败 board=${be.boardId} type=${type.id}", e)
            return null
        }
        BlackboardEvents.QUESTION_GENERATED.invoke(QuestionGeneratedEvent(level, pos, state, question, player))
        be.setQuestion(question)
        return question
    }

    /** 冻结注册表（注册阶段结束后调用；internal-core-api §10）。冻结后任何 `register` 抛错。 */
    fun freezeRegistries() {
        BlackboardRegistries.freezeAll()
    }

    /** 清空某维度的索引（维度/世界卸载时）。 */
    fun clearLevel(level: ServerLevel) {
        boards.remove(level.dimension())
    }

    /** 清空全部索引（服务器停止时）。 */
    fun clearAll() {
        boards.clear()
    }
}
