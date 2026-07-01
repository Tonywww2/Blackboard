package com.tonywww.blackboard.api.registry

import net.minecraft.resources.ResourceLocation

/**
 * A loader-agnostic, insertion-ordered registry keyed by [ResourceLocation].
 *
 * Properties relied on by the rest of the mod:
 * - **Deterministic iteration order** ([all]/[ids] follow registration order) so that weighted
 *   random question selection is reproducible for a given seed.
 * - **Tag indexing** ([byTag]) for grouping generators/types into candidate pools.
 * - **Freezing** ([freeze]) after the registration phase to reject late mutation.
 *
 * All mutating/reading operations are guarded by a single lock; [isFrozen] reads a volatile flag.
 *
 * @param name human-readable registry name, used in error messages.
 */
class SimpleRegistry<T : Any>(val name: String) {

    private val entries = LinkedHashMap<ResourceLocation, T>() // ordered: decides iteration/selection order
    private val tagIndex = HashMap<ResourceLocation, LinkedHashSet<ResourceLocation>>()
    private val idByValue = HashMap<T, ResourceLocation>()

    @Volatile
    private var frozen = false
    private val lock = Any()

    /**
     * Registers [value] under [rid] with optional [tags].
     *
     * @throws IllegalStateException if the registry is already frozen.
     * @throws IllegalArgumentException if [rid] is already registered (no silent overwrite).
     * @return the registered [value], for fluent use.
     */
    fun register(rid: ResourceLocation, value: T, tags: Set<ResourceLocation> = emptySet()): T {
        synchronized(lock) {
            check(!frozen) { "Registry '$name' is frozen; cannot register: $rid" }
            require(rid !in entries) { "Registry '$name' duplicate id: $rid" }
            entries[rid] = value
            idByValue[value] = rid
            for (t in tags) tagIndex.getOrPut(t) { LinkedHashSet() }.add(rid)
        }
        return value
    }

    /**
     * Removes the entry registered under [rid] (and its tag-index / reverse-lookup references), if
     * present. Intended for the registration/startup phase — e.g. a KubeJS script disabling a
     * built-in generator before the registry freezes.
     *
     * @throws IllegalStateException if the registry is already frozen.
     * @return the removed value, or `null` if [rid] was not registered.
     */
    fun unregister(rid: ResourceLocation): T? = synchronized(lock) {
        check(!frozen) { "Registry '$name' is frozen; cannot unregister: $rid" }
        val removed = entries.remove(rid) ?: return@synchronized null
        idByValue.remove(removed)
        for (ids in tagIndex.values) ids.remove(rid)
        removed
    }

    fun get(rid: ResourceLocation): T? = synchronized(lock) { entries[rid] }

    fun idOf(value: T): ResourceLocation? = synchronized(lock) { idByValue[value] }

    fun contains(rid: ResourceLocation): Boolean = synchronized(lock) { entries.containsKey(rid) }

    /** Ordered snapshot of all registered values. */
    fun all(): List<T> = synchronized(lock) { entries.values.toList() }

    /** Ordered snapshot of all registered ids. */
    fun ids(): List<ResourceLocation> = synchronized(lock) { entries.keys.toList() }

    /** Values registered under [tag], in registration order; empty if the tag is unknown. */
    fun byTag(tag: ResourceLocation): List<T> = synchronized(lock) {
        tagIndex[tag]?.mapNotNull { entries[it] } ?: emptyList()
    }

    fun freeze() {
        synchronized(lock) { frozen = true }
    }

    /**
     * Reopens a frozen registry for a rebuild: clears all entries/tags and unfreezes.
     *
     * Used by the runtime hot-reload pipeline (`core.GeneratorReload`) to rebuild the generator
     * registry via the `/blackboard reload` command. The coordinator runs this on the server thread
     * while the registry is briefly empty, then re-registers and [freeze]s again.
     */
    fun reopen() {
        synchronized(lock) {
            entries.clear()
            tagIndex.clear()
            idByValue.clear()
            frozen = false
        }
    }

    fun isFrozen(): Boolean = frozen
}
