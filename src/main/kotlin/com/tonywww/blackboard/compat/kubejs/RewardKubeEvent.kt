package com.tonywww.blackboard.compat.kubejs

import com.tonywww.blackboard.api.event.RewardEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Server event wrapping the native [RewardEvent]. JS can replace the loot table (set to `null` to
 * cancel the default drop) or append extra item stacks; changes write through to the native event.
 *
 * Platform boundary: only the server-event base class name differs (KubeJS 6 `ServerEventJS` vs
 * KubeJS 7 `ServerKubeEvent`).
 */
class RewardKubeEvent(server: MinecraftServer, private val native: RewardEvent)
//? if forge {
    : dev.latvian.mods.kubejs.server.ServerEventJS(server)
//?} else {
/*: dev.latvian.mods.kubejs.server.ServerKubeEvent(server)
*///?}
{
    /** The player being rewarded. */
    fun getPlayer(): ServerPlayer = native.player

    /** Current reward loot table (may be `null`). */
    fun getLootTable(): ResourceLocation? = native.lootTable

    /** Replace the reward loot table; `null` cancels the default drop. */
    fun setLootTable(table: ResourceLocation?) {
        native.lootTable = table
    }

    /** Append an extra item stack to grant on top of the loot table. */
    fun addDrop(stack: ItemStack) {
        native.extraDrops.add(stack)
    }
}
