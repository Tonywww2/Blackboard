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
        // 有效方块集 = 基础黑板 + 通过 BlackboardBoards 注册的扩展黑板方块。
        BlockEntityType.Builder.of(::BlackboardBlockEntity, *BlackboardBoards.validBlocks()).build(null)
    })

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
