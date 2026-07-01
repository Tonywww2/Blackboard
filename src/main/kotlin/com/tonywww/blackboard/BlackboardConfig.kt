package com.tonywww.blackboard

//? if forge {
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.config.ModConfig
//?} else {
/*import net.neoforged.neoforge.common.ModConfigSpec as ForgeConfigSpec
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.config.ModConfig
*///?}

/**
 * Standard loader config for Blackboard (COMMON type → `config/blackboard-common.toml`).
 *
 * Platform boundary: only the config-spec type and the FML packages differ between Forge 1.20.1 and
 * NeoForge 1.21.1. NeoForge's `ModConfigSpec` is a source-compatible fork of Forge's `ForgeConfigSpec`,
 * so it is import-aliased to `ForgeConfigSpec` and the whole body is shared.
 */
object BlackboardConfig {

    private val builder = ForgeConfigSpec.Builder()

    /**
     * Debug: when enabled, sneak (shift) right-clicking a blackboard sends the interacting player the
     * current question's text component. Off by default. Config key `debug.tellQuestionOnShiftClick`.
     */
    val tellQuestionOnShiftClick: ForgeConfigSpec.BooleanValue = builder
        .comment("Debug: sneak (shift) right-click a blackboard to have it tell you the current question's text component.")
        .define("debug.tellQuestionOnShiftClick", false)

    /**
     * Base question difficulty (0 = easiest, higher = harder). Each blackboard type may add its own
     * modifier on top (see [com.tonywww.blackboard.api.board.BlackboardType.difficultyModifier]); the
     * total is clamped to `0..10` before generation, so a negative sum just means "easiest". Config key
     * `question.difficultyBase`.
     */
    val difficultyBase: ForgeConfigSpec.IntValue = builder
        .comment("Base question difficulty (0 = easiest, higher = harder). Per-blackboard-type modifiers add to this; the total is clamped to 0..10.")
        .defineInRange("question.difficultyBase", 0, -8, 8)

    /**
     * Whether the calculus question generators (derivative-at-point, definite/indefinite integral,
     * differentiation, limit) are also added to the default blackboard pool (`#blackboard:default`), so
     * ordinary blackboards can show calculus questions. Off by default (calculus stays on its own
     * `blackboard:calculus` type). Applied on server start and `/blackboard reload`. Config key
     * `pool.calculusInDefaultPool`.
     */
    val calculusInDefaultPool: ForgeConfigSpec.BooleanValue = builder
        .comment("Whether calculus question generators are also added to the default blackboard pool, so ordinary blackboards can show calculus questions. Applied on server start / /blackboard reload.")
        .define("pool.calculusInDefaultPool", false)

    /**
     * Whether the boolean-logic question generators (`logic_eval`, `logic_assign`) are also added to the
     * default blackboard pool (`#blackboard:default`), so ordinary blackboards can show logic questions.
     * Off by default (logic stays on its own `blackboard:logic` type). Applied on server start and
     * `/blackboard reload`. Config key `pool.logicInDefaultPool`.
     */
    val logicInDefaultPool: ForgeConfigSpec.BooleanValue = builder
        .comment("Whether boolean-logic question generators are also added to the default blackboard pool, so ordinary blackboards can show logic questions. Applied on server start / /blackboard reload.")
        .define("pool.logicInDefaultPool", false)

    /**
     * Whether the linear-algebra question generators (dot product, transpose, matrix-vector product,
     * inverse) are also added to the default blackboard pool (`#blackboard:default`), so ordinary
     * blackboards can show linear-algebra questions. Off by default (they stay on the
     * `blackboard:linear_algebra` type). Applied on server start and `/blackboard reload`. Config key
     * `pool.linearAlgebraInDefaultPool`.
     */
    val linearAlgebraInDefaultPool: ForgeConfigSpec.BooleanValue = builder
        .comment("Whether the linear-algebra question generators (dot product, transpose, matrix-vector product, inverse) are also added to the default blackboard pool, so ordinary blackboards can show linear-algebra questions. Applied on server start / /blackboard reload.")
        .define("pool.linearAlgebraInDefaultPool", false)

    /** The built config spec, registered by [register]. */
    val SPEC: ForgeConfigSpec = builder.build()

    /** Registers the COMMON config spec. Call during mod construction. */
    fun register() {
        //? if forge {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC)
        //?} else {
        /*ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.COMMON, SPEC)
        *///?}
    }
}
