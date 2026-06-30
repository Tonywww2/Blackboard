package com.tonywww.blackboard.api.question

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState

/** 作答上下文（服务端）。 */
interface AnswerContext {
    val player: ServerPlayer
    val level: ServerLevel
    val pos: BlockPos
    val blockState: BlockState

    /** 玩家提交的原始答案文本（已去掉格式前缀与黑板标识）。 */
    val text: String
}
