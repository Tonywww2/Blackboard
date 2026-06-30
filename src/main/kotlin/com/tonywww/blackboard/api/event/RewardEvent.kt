package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.Question
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

/**
 * 答对发奖前广播：监听器可替换 [lootTable]（设为 null 取消默认发放）或追加 [extraDrops]，
 * 实现完全自定义的奖励逻辑。见 design §6.2 / internal-core-api §7。
 */
class RewardEvent(
    val level: ServerLevel,
    val pos: BlockPos,
    val blockState: BlockState,
    val player: ServerPlayer,
    val question: Question,
    val result: AnswerResult.Correct,
    /** 默认战利品表（可被替换为 null 取消默认发放）。 */
    var lootTable: ResourceLocation?,
    /** 额外掉落/给予的物品（开发者自定义奖励逻辑）。 */
    val extraDrops: MutableList<ItemStack>,
)
