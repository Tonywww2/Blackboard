package com.tonywww.blackboard.client

//? if forge {
import net.minecraftforge.fml.ModList
//?} else {
/*import net.neoforged.fml.ModList
*///?}

import com.tonywww.blackboard.api.render.BlackboardRendering
import org.slf4j.LoggerFactory

/**
 * Client bootstrap (P8-A): installs the ApricityUI world renderer when ApricityUI is present.
 *
 * Called from the mod entry point during client setup. Without ApricityUI the default no-op renderer
 * (see [BlackboardRendering]) stays in place, so the mod runs unchanged — this is why the AUI classes
 * are only touched **after** the `isLoaded` guard (touching [AuiBlackboardRenderer] otherwise would
 * `NoClassDefFound` on a client without AUI, same pattern as the KubeJS guard).
 */
object BlackboardClient {
    private val logger = LoggerFactory.getLogger("blackboard")

    fun init() {
        if (!ModList.get().isLoaded("apricityui")) return
        BlackboardRendering.renderer = AuiBlackboardRenderer()
        logger.info("ApricityUI detected: blackboard world renderer enabled")
    }
}
