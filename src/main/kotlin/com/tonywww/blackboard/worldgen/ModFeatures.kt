package com.tonywww.blackboard.worldgen

import com.tonywww.blackboard.Blackboard
import com.tonywww.blackboard.platform.PlatformRegistration
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus
//?} else {
/*import net.neoforged.bus.api.IEventBus
*///?}
import java.util.function.Supplier

/** 世界生成特征注册。用模组事件总线在构造期调用 [register]。 */
object ModFeatures {
    private val REGISTER = PlatformRegistration.features()

    /** `blackboard:blackboard` 特征（供数据包 `worldgen/configured_feature` 引用）。 */
    val BLACKBOARD: Supplier<Feature<NoneFeatureConfiguration>> =
        REGISTER.register(Blackboard.MOD_ID) { BlackboardFeature(NoneFeatureConfiguration.CODEC) }

    fun register(bus: IEventBus) {
        REGISTER.register(bus)
    }
}
