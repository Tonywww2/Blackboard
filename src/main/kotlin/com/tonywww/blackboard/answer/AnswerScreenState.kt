package com.tonywww.blackboard.answer

import com.tonywww.blackboard.client.AnswerScreenInjector
import net.minecraft.core.BlockPos
//? if forge {
import net.minecraftforge.fml.ModList
//?} else {
/*import net.neoforged.fml.ModList
*///?}

/**
 * Shared state / entry point for the chalk answer screen.
 *
 * The answer screen is a container-bound AUI screen (server-authoritative), but AUI's own
 * `ApricityUI.openScreen(path)` — present on **both** loaders — already drives that open handshake from
 * the client, so the chalk interaction only runs client-side: [openOnClient] records the clicked board
 * (so [com.tonywww.blackboard.client.AnswerScreenInjector] can locate its block entity when the screen
 * opens) and asks AUI to open the screen. It is a no-op without ApricityUI; the AUI-touching
 * [AnswerScreenInjector] is referenced only past the `isLoaded` guard.
 */
object AnswerScreenState {

    /** AUI template path (under `assets/apricityui/apricity/`). */
    const val TEMPLATE = "blackboard/answer.html"

    @Volatile
    @JvmStatic
    var pendingPos: BlockPos? = null
        private set

    /** Client: remember the clicked board and open the AUI answer screen (no-op without ApricityUI). */
    fun openOnClient(pos: BlockPos) {
        pendingPos = pos.immutable()
        if (ModList.get().isLoaded("apricityui")) AnswerScreenInjector.open()
    }
}
