package com.tonywww.blackboard.content

import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.block.Block
//? if forge {
import net.minecraft.world.level.Level
//?} else {
/*import net.minecraft.core.component.DataComponents
*///?}

/**
 * `BlockItem` for blackboard blocks that carries an optional **pinned question-generator id** in its
 * block-entity NBT (key `Generator`) and shows it as a tooltip.
 *
 * When such a stack is placed, the vanilla block-entity-data mechanism copies that NBT into the new
 * [BlackboardBlockEntity] (before `setPlacedBy` runs), so generation pins that generator —
 * `BlackboardManager` falls back to the type's normal selection when the id is absent or unknown.
 *
 * Platform boundary: `appendHoverText` has a different second parameter on 1.20.1 (`Level?`) vs
 * 1.21.1 (`Item.TooltipContext`), and reading item block-entity data differs (static
 * `BlockItem.getBlockEntityData` vs the `BLOCK_ENTITY_DATA` data component).
 */
class BlackboardBlockItem(block: Block, properties: Item.Properties) : BlockItem(block, properties) {

    //? if forge {
    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        appendGeneratorLine(stack, tooltip)
        super.appendHoverText(stack, level, tooltip, flag)
    }
    //?} else {
    /*override fun appendHoverText(stack: ItemStack, context: Item.TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        appendGeneratorLine(stack, tooltip)
        super.appendHoverText(stack, context, tooltip, flag)
    }
    *///?}

    private fun appendGeneratorLine(stack: ItemStack, tooltip: MutableList<Component>) {
        val id = readGeneratorId(stack) ?: return
        tooltip.add(
            Component.translatable("tooltip.blackboard.generator", id.toString()).withStyle(ChatFormatting.GRAY),
        )
    }

    companion object {
        private const val KEY_GENERATOR = "Generator"

        /** Creates a stack of [item] whose placed block entity will pin [generatorId]. */
        @JvmStatic
        fun stackWithGenerator(item: Item, generatorId: ResourceLocation): ItemStack {
            val stack = ItemStack(item)
            val data = CompoundTag().apply { putString(KEY_GENERATOR, generatorId.toString()) }
            BlockItem.setBlockEntityData(stack, ModBlockEntities.BLACKBOARD.get(), data)
            return stack
        }

        /** Reads the pinned generator id from a stack's block-entity NBT, or `null` (null-safe parse). */
        @JvmStatic
        fun readGeneratorId(stack: ItemStack): ResourceLocation? {
            val data = blockEntityData(stack) ?: return null
            if (!data.contains(KEY_GENERATOR)) return null
            return ResourceLocation.tryParse(data.getString(KEY_GENERATOR))
        }

        private fun blockEntityData(stack: ItemStack): CompoundTag? =
            //? if forge {
            BlockItem.getBlockEntityData(stack)
            //?} else {
            /*stack.get(DataComponents.BLOCK_ENTITY_DATA)?.copyTag()
            *///?}
    }
}
