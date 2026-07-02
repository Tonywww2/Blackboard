package com.tonywww.blackboard.content

import com.tonywww.blackboard.Blackboard
import com.tonywww.blackboard.platform.PlatformRegistration
import net.minecraft.world.item.Item
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus
//?} else {
/*import net.neoforged.bus.api.IEventBus
*///?}
import java.util.function.Supplier

/** Item registry for Blackboard (the `BlockItem` for the blackboard block). */
object ModItems {
    internal val REGISTER = PlatformRegistration.items()

    val BLACKBOARD: Supplier<Item> = REGISTER.register(Blackboard.MOD_ID, Supplier<Item> {
        BlackboardBlockItem(ModBlocks.BLACKBOARD.get(), Item.Properties())
    })

    /** Chalk: right-click a blackboard to open the ApricityUI answer screen (see [ChalkItem]). */
    val CHALK: Supplier<Item> = REGISTER.register("chalk", Supplier<Item> {
        ChalkItem(Item.Properties())
    })

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
