package com.tonywww.blackboard.api.render

/**
 * Renders a blackboard's question into the world.
 *
 * This is a pure contract: the core mod never renders anything itself. Integrations such as
 * ApricityUI implement this interface and register it through [BlackboardRendering.renderer].
 * When no renderer is installed, the default no-op keeps the gameplay loop (question / answer /
 * reward) fully functional without any rendering dependency.
 */
fun interface BlackboardRenderer {
    /**
     * Render the blackboard described by [context].
     *
     * Implementations are expected to run client-side and must tolerate being called for any
     * blackboard whose [RenderContext.content] has changed.
     */
    fun render(context: RenderContext)
}
