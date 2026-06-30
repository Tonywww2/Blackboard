package com.tonywww.blackboard.content

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
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

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack,
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (!level.isClientSide) {
            (level.getBlockEntity(pos) as? BlackboardBlockEntity)?.assignBoardIdIfAbsent(level.random)
        }
    }

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }
}
