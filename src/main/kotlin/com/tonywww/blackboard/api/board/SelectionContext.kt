package com.tonywww.blackboard.api.board

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState

/** 选题上下文。 */
interface SelectionContext {
    val blackboard: BlackboardType
    val level: ServerLevel
    val pos: BlockPos
    val blockState: BlockState
    val player: ServerPlayer?
}
