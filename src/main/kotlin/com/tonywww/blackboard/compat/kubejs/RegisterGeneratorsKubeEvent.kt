package com.tonywww.blackboard.compat.kubejs

import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register
import net.minecraft.resources.ResourceLocation

/**
 * Startup event: `BlackboardEvents.registerGenerators(event => event.register(generator))`.
 *
 * Lets KubeJS scripts contribute [QuestionGenerator]s into the global registry before it freezes.
 *
 * Platform boundary: the startup-event base class differs between KubeJS 6 (`StartupEventJS`, a
 * class) and KubeJS 7 (`KubeStartupEvent`, an interface); only the supertype is branched.
 */
class RegisterGeneratorsKubeEvent
//? if forge {
    : dev.latvian.mods.kubejs.event.StartupEventJS()
//?} else {
/*: dev.latvian.mods.kubejs.event.KubeStartupEvent
*///?}
{
    /** Register a [generator] (build it with `QuestionGenerator.builder(id)...`). */
    fun register(generator: QuestionGenerator) {
        BlackboardRegistries.QUESTION_GENERATORS.register(generator)
    }

    /**
     * Remove/disable an already-registered generator by [id]. Runs during KubeJS startup — before the
     * registry is snapshotted as the startup baseline and frozen — so the generator is excluded from
     * the baseline and stays disabled across `/blackboard reload`. No-op if [id] is unknown.
     */
    fun remove(id: ResourceLocation) {
        BlackboardRegistries.QUESTION_GENERATORS.unregister(id)
    }
}
