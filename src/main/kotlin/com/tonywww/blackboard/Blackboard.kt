package com.tonywww.blackboard

//? if forge {
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
//?} else {
/*import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
*///?}

import com.tonywww.blackboard.builtin.BuiltinBlackboardTypes
import com.tonywww.blackboard.builtin.BuiltinGenerators
import com.tonywww.blackboard.builtin.calculus.CalculusGenerators
import com.tonywww.blackboard.compat.kubejs.BlackboardKubePlugin
import com.tonywww.blackboard.content.ModBlockEntities
import com.tonywww.blackboard.content.ModBlocks
import com.tonywww.blackboard.content.ModCreativeTabs
import com.tonywww.blackboard.content.ModItems
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

        // Deferred content onto the KLF mod bus.
        ModBlocks.register(bus)
        ModItems.register(bus)
        ModBlockEntities.register(bus)
        ModCreativeTabs.register(bus)

        // Built-in generators then types (order per P5-B); registered into the later-frozen registries.
        BuiltinGenerators.register()
        BuiltinBlackboardTypes.register()

        // Calculus module generators + its ByTag(CALCULUS) blackboard type (before freeze).
        CalculusGenerators.register()
        CalculusGenerators.registerType()

        // Soft dependency: only touch the KubeJS plugin class when KubeJS is loaded, otherwise its
        // KubeJS superclass would NoClassDefFound on a KubeJS-less install (P4-C).
        if (ModList.get().isLoaded("kubejs")) {
            BlackboardKubePlugin.register(bus)
            logger.info("KubeJS detected: Blackboard integration enabled")
        }

        logger.info("Blackboard loaded")
    }
}
