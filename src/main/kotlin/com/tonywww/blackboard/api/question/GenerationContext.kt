package com.tonywww.blackboard.api.question

import com.tonywww.blackboard.api.board.BlackboardType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.state.BlockState

/** 出题上下文（服务端）。 */
interface GenerationContext {
    val level: ServerLevel
    val pos: BlockPos
    val blockState: BlockState
    val blackboard: BlackboardType
    val random: RandomSource

    /** 触发出题的玩家（如有）。 */
    val player: ServerPlayer?

    /** 难度参数，可由黑板类型或方块状态提供（默认 0）。 */
    val difficulty: Int
}
