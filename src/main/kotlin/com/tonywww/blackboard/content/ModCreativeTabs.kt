package com.tonywww.blackboard.content

import com.tonywww.blackboard.Blackboard
import com.tonywww.blackboard.api.registry.BlackboardRegistries
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
                // 粉笔：右键黑板打开作答界面。
                output.accept(ItemStack(ModItems.CHALK.get()))
                // 基础/扩展黑板方块（各自随机出题）。
                BlackboardBoards.tabItems().forEach { output.accept(it.get()) }
                // 每个已注册生成器一份「基础黑板」变体（放置时绑定该生成器）。
                val base = ModItems.BLACKBOARD.get()
                BlackboardRegistries.QUESTION_GENERATORS.ids().forEach { genId ->
                    output.accept(BlackboardBlockItem.stackWithGenerator(base, genId))
                }
            }
            .build()
    })

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
