package com.tonywww.blackboard.compat.kubejs

import com.tonywww.blackboard.api.event.BlackboardEvents
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
//?} else {
/*import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
*///?}

/**
 * KubeJS plugin entrypoint (declared in `kubejs.plugins.txt`).
 *
 * Soft dependency: this class references KubeJS types, so it is only ever loaded when KubeJS is
 * present (KubeJS reads `kubejs.plugins.txt`). The mod entrypoint (P7-B) must guard [register] with
 * a KubeJS-loaded check so the class is never touched on a KubeJS-less install.
 *
 * Platform boundary: the plugin base differs between KubeJS 6 (`KubeJSPlugin`, a class with a
 * no-arg `registerEvents()`) and KubeJS 7 (`...plugin.KubeJSPlugin`, an interface whose
 * `registerEvents(EventGroupRegistry)` takes a registry).
 */
class BlackboardKubePlugin
//? if forge {
    : dev.latvian.mods.kubejs.KubeJSPlugin()
//?} else {
/*: dev.latvian.mods.kubejs.plugin.KubeJSPlugin
*///?}
{
    //? if forge {
    override fun registerEvents() {
        BlackboardKubeEvents.GROUP.register()
    }
    //?} else {
    /*override fun registerEvents(registry: dev.latvian.mods.kubejs.event.EventGroupRegistry) {
        registry.register(BlackboardKubeEvents.GROUP)
    }
    *///?}

    companion object {
        /**
         * Wires KubeJS support: bridges the native Blackboard events to the KubeJS event group and
         * schedules the startup generator-registration event. Call from the (KubeJS-guarded) mod
         * entrypoint with the mod event bus.
         */
        @JvmStatic
        fun register(bus: IEventBus) {
            bridgeNativeEvents()
            bus.addListener { _: FMLCommonSetupEvent -> postRegisterGenerators() }
        }

        private fun bridgeNativeEvents() {
            BlackboardEvents.SELECT_GENERATOR.register { event ->
                BlackboardKubeEvents.SELECT_GENERATOR.post(
                    SelectGeneratorKubeEvent(event.context.level.server, event),
                )
            }
            BlackboardEvents.REWARD.register { event ->
                BlackboardKubeEvents.REWARD.post(RewardKubeEvent(event.level.server, event))
            }
        }

        private fun postRegisterGenerators() {
            BlackboardKubeEvents.REGISTER_GENERATORS.post(RegisterGeneratorsKubeEvent())
        }
    }
}
