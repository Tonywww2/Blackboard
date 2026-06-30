package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.SelectionContext
import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.GenerationContext
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState

/**
 * 三个上下文接口的内部数据实现。
 *
 * 均为只读数据载体（[GenerationContext] / [AnswerContext] / [SelectionContext]），由服务端流程
 * 构造后传入生成器 / 选题器 / 判题逻辑。参考 internal-core-api §5。
 */

/** [GenerationContext] 实现：出题时由方块实体 / 管理器构造。 */
internal data class GenerationContextImpl(
    override val level: ServerLevel,
    override val pos: BlockPos,
    override val blockState: BlockState,
    override val blackboard: BlackboardType,
    override val random: RandomSource,
    override val player: ServerPlayer?,
    override val difficulty: Int,
) : GenerationContext

/** [AnswerContext] 实现：作答时由作答处理流程构造。 */
internal data class AnswerContextImpl(
    override val player: ServerPlayer,
    override val level: ServerLevel,
    override val pos: BlockPos,
    override val blockState: BlockState,
    override val text: String,
) : AnswerContext

/** [SelectionContext] 实现：选题时由选题流程构造。 */
internal data class SelectionContextImpl(
    override val blackboard: BlackboardType,
    override val level: ServerLevel,
    override val pos: BlockPos,
    override val blockState: BlockState,
    override val player: ServerPlayer?,
) : SelectionContext
