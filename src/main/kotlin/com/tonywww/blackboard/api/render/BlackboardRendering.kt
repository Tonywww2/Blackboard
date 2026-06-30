package com.tonywww.blackboard.api.render

/**
 * Global holder for the active [BlackboardRenderer].
 *
 * Defaults to a no-op renderer so the mod runs without any rendering backend. An integration
 * (e.g. ApricityUI, wired up during client setup) replaces [renderer] to enable rendering; no
 * core logic changes are required.
 */
object BlackboardRendering {
    /** Active renderer; no-op by default. Replace during client initialization to enable rendering. */
    @JvmStatic
    var renderer: BlackboardRenderer = BlackboardRenderer { /* no-op placeholder */ }
}
