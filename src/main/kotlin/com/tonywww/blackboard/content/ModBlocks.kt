package com.tonywww.blackboard.content

import com.tonywww.blackboard.Blackboard
import com.tonywww.blackboard.platform.PlatformRegistration
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus
//?} else {
/*import net.neoforged.bus.api.IEventBus
*///?}
import java.util.function.Supplier

/** Block registry for Blackboard. Call [register] with the mod event bus during construction. */
object ModBlocks {
    internal val REGISTER = PlatformRegistration.blocks()

    val BLACKBOARD: Supplier<Block> = REGISTER.register(Blackboard.MOD_ID, Supplier<Block> { BlackboardBlock(blockProps()) })

    private fun blockProps(): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5f)
            // 非满方块/透明模型：关闭遮挡，避免相邻方块的面被错误剔除。
            .noOcclusion()

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
