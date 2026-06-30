package com.tonywww.blackboard.api.event

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A minimal, loader-agnostic event hook.
 *
 * Guarantees:
 * - **Registration order == invocation order.**
 * - **Listener isolation:** an exception thrown by one listener is logged and does not prevent the
 *   remaining listeners from running.
 * - **Thread-safe registration** via a [CopyOnWriteArrayList].
 *
 * Usage: create one [EventHook] per event type and expose them from an aggregator object
 * (e.g. `BlackboardEvents`, defined separately).
 *
 * @param debugName short name used for the logger and error messages.
 */
class EventHook<T>(private val debugName: String) {

    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()
    private val log = LoggerFactory.getLogger("Blackboard/$debugName")

    /** Adds a listener. Listeners are invoked in registration order. */
    fun register(listener: (T) -> Unit) {
        listeners += listener
    }

    /** Invokes every registered listener with [event]; exceptions are caught and logged. */
    fun invoke(event: T) {
        for (l in listeners) {
            try {
                l(event)
            } catch (e: Throwable) {
                log.error("Event listener threw ($debugName)", e)
            }
        }
    }
}
