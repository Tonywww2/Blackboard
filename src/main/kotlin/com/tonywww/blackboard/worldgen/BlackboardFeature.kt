package com.tonywww.blackboard.worldgen

import com.mojang.serialization.Codec
import com.tonywww.blackboard.content.BlackboardBlock
import com.tonywww.blackboard.content.ModBlocks
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration

/**
 * 简单地表特征：在 `origin`（放置修饰符已按高度图定位到地表空气格）放置一块朝向随机的黑板方块。
 *
 * 世界生成放置的黑板此刻尚未初始化（无 `boardId`/类型/题目）——世界生成阶段用的是 `WorldGenRegion` 而非
 * 完整 `ServerLevel`，不宜在此选题/出题。等其区块加载进真实 `ServerLevel` 时，
 * `PlatformEvents.onChunkLoad` 会对未初始化的黑板调用 `BlackboardManager.onPlaced` 补全并出题。
 */
class BlackboardFeature(codec: Codec<NoneFeatureConfiguration>) : Feature<NoneFeatureConfiguration>(codec) {

    override fun place(context: FeaturePlaceContext<NoneFeatureConfiguration>): Boolean {
        val level = context.level()
        val pos = context.origin()
        val below = pos.below()
        // 目标格须为空气，其下须是可站立的实心朝上面（避免浮空 / 水中 / 树叶顶上）。
        if (!level.getBlockState(pos).isAir) return false
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false
        val facing = Direction.Plane.HORIZONTAL.getRandomDirection(context.random())
        val state = ModBlocks.BLACKBOARD.get().defaultBlockState().setValue(BlackboardBlock.FACING, facing)
        return level.setBlock(pos, state, Block.UPDATE_CLIENTS)
    }
}
