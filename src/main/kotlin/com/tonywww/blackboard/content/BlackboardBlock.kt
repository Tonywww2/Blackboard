package com.tonywww.blackboard.content

import com.tonywww.blackboard.BlackboardConfig
import com.tonywww.blackboard.core.BlackboardManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
//? if forge {
import net.minecraft.world.InteractionHand
//?}
//? if neoforge {
/*import com.mojang.serialization.MapCodec
*///?}

/**
 * The blackboard block: a horizontally-facing [BaseEntityBlock] backed by [BlackboardBlockEntity].
 *
 * The [FACING] property matches the shipped blockstate (north/east/south/west variants). On 1.21.1
 * `BaseEntityBlock` requires a `codec()`; that single override is the only platform difference.
 */
class BlackboardBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    //? if neoforge {
    /*override fun codec(): MapCodec<BlackboardBlock> = simpleCodec(::BlackboardBlock)
    *///?}

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = BlackboardBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    //? if forge {
    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult = tellQuestion(level, pos, player)
    //?} else {
    /*override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult,
    ): InteractionResult = tellQuestion(level, pos, player)
    *///?}

    /**
     * Debug interaction: when [BlackboardConfig.tellQuestionOnShiftClick] is enabled and the player is
     * sneaking, sends them the current question's text component (server-side). Otherwise returns
     * [InteractionResult.PASS] so normal (non-sneak) interaction and placement are unaffected.
     */
    private fun tellQuestion(level: Level, pos: BlockPos, player: Player): InteractionResult {
        if (!player.isSecondaryUseActive) return InteractionResult.PASS
        if (!BlackboardConfig.tellQuestionOnShiftClick.get()) return InteractionResult.PASS
        if (!level.isClientSide) {
            val content = (level.getBlockEntity(pos) as? BlackboardBlockEntity)?.question?.content
            player.sendSystemMessage(content ?: Component.literal("[Blackboard] 该黑板暂无题目"))
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack,
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            val be = level.getBlockEntity(pos) as? BlackboardBlockEntity ?: return
            // 分配 boardId + 默认黑板类型、登记索引、并出第一题（见 BlackboardManager.onPlaced）。
            BlackboardManager.onPlaced(be, placer as? ServerPlayer)
        }
    }

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }
}
