package com.tonywww.blackboard.platform

import net.minecraft.core.HolderLookup
import net.minecraft.network.chat.Component

/**
 * Serializes blackboard question text ([Component]) to/from JSON for persistence.
 *
 * 1.20.1 uses the registry-free `Component.Serializer`; 1.21.1's serializer requires a
 * [HolderLookup.Provider]. The provider parameter is kept on both platforms so callers (e.g.
 * `QuestionNbt`) need no branch of their own; it is simply ignored on Forge 1.20.1.
 *
 * 🟥 C6: signatures verified against the compiled Mojang-mapped runtime on both nodes.
 */
object PlatformComponents {
    /** Serialize [component] to a JSON string. */
    @JvmStatic
    fun toJson(component: Component, registries: HolderLookup.Provider): String =
        //? if forge {
        Component.Serializer.toJson(component)
        //?} else {
        /*Component.Serializer.toJson(component, registries)
        *///?}

    /** Deserialize a [Component] from a JSON string; returns `null` if the JSON is invalid. */
    @JvmStatic
    fun fromJson(json: String, registries: HolderLookup.Provider): Component? =
        //? if forge {
        Component.Serializer.fromJson(json)
        //?} else {
        /*Component.Serializer.fromJson(json, registries)
        *///?}
}
