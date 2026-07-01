package com.tonywww.blackboard

//? if forge {
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
//?} else {
/*import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
*///?}

import com.tonywww.blackboard.builtin.BuiltinBlackboardTypes
import com.tonywww.blackboard.builtin.BuiltinGenerators
import com.tonywww.blackboard.builtin.calculus.CalculusGenerators
import com.tonywww.blackboard.client.BlackboardClient
import com.tonywww.blackboard.compat.kubejs.BlackboardKubePlugin
import com.tonywww.blackboard.content.ModBlockEntities
import com.tonywww.blackboard.content.ModBlocks
import com.tonywww.blackboard.content.ModCreativeTabs
import com.tonywww.blackboard.content.ModItems
import com.tonywww.blackboard.worldgen.ModFeatures
import dev.nyon.klf.MOD_BUS
import org.slf4j.LoggerFactory

/**
 * Blackboard mod entry point.
 *
 * Cross-compiled for Forge 1.20.1 and NeoForge 1.21.1 via Stonecutter (flat) + Architectury Loom,
 * with Kotlin provided by KotlinLangForge (`modLoader = "klf"`).
 *
 * Mod-construction wiring (P7-B):
 * - registers deferred content (blocks, items, block entities, creative tabs) onto the KLF mod bus;
 * - registers built-in question generators then blackboard types into the global registries
 *   (internal-core-api §10 common-init phase — before P7-A freezes them at server start);
 * - enables the KubeJS integration only when KubeJS is installed (soft dependency).
 *
 * Out of scope here: the client renderer is injected by P8-A (`client/BlackboardClient`) during
 * client setup (the default [BlackboardRendering][com.tonywww.blackboard.api.render.BlackboardRendering]
 * renderer is a no-op); registry freezing and server/chat lifecycle hooks are handled by P7-A.
 */
@Mod(Blackboard.MOD_ID)
object Blackboard {
    const val MOD_ID = "blackboard"

    private val logger = LoggerFactory.getLogger(MOD_ID)

    init {
        val bus = MOD_BUS

        // Standard loader config (COMMON → config/blackboard-common.toml).
        BlackboardConfig.register()

        // NeoForge: KLF 的 langprovider 处于 PLUGIN 模块层，无法读取 GAME 层的 NeoForge 事件总线
        // （其 AutomaticEventSubscriber.getGameBus 会抛 IllegalAccessError），故这里在 GAME 层的 mod
        // 构造代码里手动把游戏总线订阅者接到 NeoForge 事件总线；Forge 仍由 KLF @EventBusSubscriber 自动扫描。
        //? if neoforge {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.tonywww.blackboard.platform.PlatformEvents)
        *///?}

        // Deferred content onto the KLF mod bus.
        ModBlocks.register(bus)
        ModItems.register(bus)
        ModBlockEntities.register(bus)
        ModCreativeTabs.register(bus)
        ModFeatures.register(bus)

        // Built-in generators then types (order per P5-B); registered into the later-frozen registries.
        BuiltinGenerators.register()
        BuiltinBlackboardTypes.register()

        // Calculus module generators + its ByTag(CALCULUS) blackboard type (before freeze). Generators go
        // through the reloadable REGISTER_GENERATORS layer so pool.calculusInDefaultPool (config, not yet
        // loaded at construction) can be read on server start / /blackboard reload.
        CalculusGenerators.registerReloadable()
        CalculusGenerators.registerType()

        // Soft dependency: only touch the KubeJS plugin class when KubeJS is loaded, otherwise its
        // KubeJS superclass would NoClassDefFound on a KubeJS-less install (P4-C).
        if (ModList.get().isLoaded("kubejs")) {
            BlackboardKubePlugin.register(bus)
            logger.info("KubeJS detected: Blackboard integration enabled")
        }

        // Client-only: inject the ApricityUI world renderer during client setup. FMLClientSetupEvent
        // only fires on the client, and BlackboardClient no-ops without ApricityUI, so the default
        // no-op renderer stays on dedicated servers / AUI-less installs.
        bus.addListener { _: FMLClientSetupEvent -> BlackboardClient.init() }

        logger.info("Blackboard loaded")
    }
}
