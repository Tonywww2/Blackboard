package com.tonywww.blackboard.content

import com.tonywww.blackboard.Blackboard
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
//?} else {
/*import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
*///?}
import java.util.function.Supplier

/**
 * Creative mode tab(s) for Blackboard.
 *
 * Creative tabs use the vanilla `Registries.CREATIVE_MODE_TAB` key on **both** loaders (only the
 * `DeferredRegister` class differs by package), so unlike blocks/items this does not go through
 * `PlatformRegistration`. The tab title uses the `itemGroup.blackboard` lang key (provided by P1-F).
 */
object ModCreativeTabs {
    private val REGISTER: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Blackboard.MOD_ID)

    /** Main Blackboard tab; currently holds the blackboard block item. */
    val BLACKBOARD: Supplier<CreativeModeTab> = REGISTER.register(Blackboard.MOD_ID, Supplier {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.${Blackboard.MOD_ID}"))
            .icon { ItemStack(ModItems.BLACKBOARD.get()) }
            .displayItems { _, output ->
                BlackboardBoards.tabItems().forEach { output.accept(it.get()) }
            }
            .build()
    })

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
