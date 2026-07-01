package com.tonywww.blackboard.api

import net.minecraft.resources.ResourceLocation

/**
 * Stable, loader-agnostic constants and helpers for the Blackboard public API.
 *
 * The only platform difference here is how a [ResourceLocation] is constructed: the public
 * constructor on Forge 1.20.1 versus the `fromNamespaceAndPath` factory on NeoForge 1.21.1 (where
 * the constructor is non-public). Everything else is shared.
 */
object BlackboardApi {
    /** Mod id / resource namespace. */
    const val MOD_ID: String = "blackboard"

    /** Build a [ResourceLocation] under the Blackboard namespace. */
    @JvmStatic
    @Suppress("DEPRECATION") // 1.20.1's public ResourceLocation(ns, path) constructor is the standard path.
    fun id(path: String): ResourceLocation =
        //? if forge {
        ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
        //?} else {
        /*ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
        *///?}
    /** Well-known id of the built-in default blackboard type (`blackboard:default`). */
    @JvmField
    val DEFAULT_TYPE_ID: ResourceLocation = id("default")
    /** Built-in tags used to group generators / blackboard pools. */
    object BlackboardTags {
        @JvmField
        val DEFAULT: ResourceLocation = id("default")

        @JvmField
        val MATH: ResourceLocation = id("math")

        @JvmField
        val TEXT: ResourceLocation = id("text")

        /** 微积分题（导数/积分/极限等），归入 `#blackboard:calculus` 池。 */
        @JvmField
        val CALCULUS: ResourceLocation = id("calculus")

        /** 逻辑值运算题，归入 `#blackboard:logic` 池。 */
        @JvmField
        val LOGIC: ResourceLocation = id("logic")

        /** 线性代数题（点积/转置/矩阵-向量积/逆矩阵等），归入 `#blackboard:linear_algebra` 池。 */
        @JvmField
        val LINEAR_ALGEBRA: ResourceLocation = id("linear_algebra")
    }
}
