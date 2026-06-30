package com.tonywww.blackboard.api.render

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Read-only context handed to a [BlackboardRenderer] when a blackboard needs to be rendered.
 *
 * The mod itself only produces the question [content] as a [Component] (which may contain LaTeX);
 * the actual rendering is delegated to an injected renderer (e.g. ApricityUI), never to core code.
 *
 * Loader-agnostic: every referenced Minecraft type shares the same package on Forge 1.20.1 and
 * NeoForge 1.21.1 (official Mojang mappings), so no Stonecutter platform split is required.
 */
interface RenderContext {
    /** The level the blackboard lives in. */
    val level: Level

    /** Position of the blackboard block. */
    val pos: BlockPos

    /** Current block state of the blackboard. */
    val blockState: BlockState

    /** Question text to render; may contain LaTeX and is passed verbatim to the renderer. */
    val content: Component
}
