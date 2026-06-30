package com.tonywww.blackboard.platform

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
//? if neoforge {
/*import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
*///?}

/**
 * Loader-agnostic loot helpers: rolling a loot table for rewards and giving/dropping stacks.
 *
 * The loot-table lookup differs between versions (1.20.1 `MinecraftServer.getLootData()` keyed by a
 * `ResourceLocation`, vs 1.21.1 `MinecraftServer.reloadableRegistries()` keyed by a
 * `ResourceKey<LootTable>`); the rolling and giving logic is shared.
 *
 * 🟥 C5: signatures verified against the compiled Mojang-mapped runtime on both nodes.
 */
object PlatformLoot {
    /** Roll [tableId] at [pos], returning the generated stacks (empty if the table is missing). */
    @JvmStatic
    fun rollLootTable(
        level: ServerLevel,
        player: ServerPlayer?,
        pos: BlockPos,
        tableId: ResourceLocation,
    ): List<ItemStack> {
        val server = level.server
        //? if forge {
        val table: LootTable = server.lootData.getLootTable(tableId)
        //?} else {
        /*val table: LootTable = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId))
        *///?}
        val builder = LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
        val params = if (player != null) {
            builder.withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT)
        } else {
            builder.create(LootContextParamSets.CHEST)
        }
        return table.getRandomItems(params)
    }

    /** Add [stack] to [player]'s inventory, dropping any remainder at the player. */
    @JvmStatic
    fun giveOrDrop(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        player.inventory.add(stack)
        if (!stack.isEmpty) {
            player.drop(stack, false)
        }
    }
}
