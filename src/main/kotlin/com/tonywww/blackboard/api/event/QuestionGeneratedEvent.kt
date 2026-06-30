package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.question.Question
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.state.BlockState

/** 题目生成后广播：可读取/二次处理（统计、日志、改写渲染）。 */
class QuestionGeneratedEvent(
    val level: ServerLevel,
    val pos: BlockPos,
    val blockState: BlockState,
    val question: Question,
    val player: ServerPlayer?,
)
