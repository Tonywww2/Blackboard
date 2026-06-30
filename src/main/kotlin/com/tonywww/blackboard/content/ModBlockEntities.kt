package com.tonywww.blackboard.content

import com.tonywww.blackboard.Blackboard
import com.tonywww.blackboard.platform.PlatformRegistration
import net.minecraft.world.level.block.entity.BlockEntityType
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus
//?} else {
/*import net.neoforged.bus.api.IEventBus
*///?}
import java.util.function.Supplier

/** Block-entity-type registry for Blackboard. */
object ModBlockEntities {
    private val REGISTER = PlatformRegistration.blockEntities()

    val BLACKBOARD: Supplier<BlockEntityType<BlackboardBlockEntity>> = REGISTER.register(Blackboard.MOD_ID, Supplier<BlockEntityType<BlackboardBlockEntity>> {
        BlockEntityType.Builder.of(::BlackboardBlockEntity, ModBlocks.BLACKBOARD.get()).build(null)
    })

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
