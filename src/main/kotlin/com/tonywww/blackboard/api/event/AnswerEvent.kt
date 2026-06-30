package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.Question
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState

/** 作答判定后广播（三态结果均会广播）。 */
class AnswerEvent(
    val level: ServerLevel,
    val pos: BlockPos,
    val blockState: BlockState,
    val player: ServerPlayer,
    val question: Question,
    val result: AnswerResult,
)
