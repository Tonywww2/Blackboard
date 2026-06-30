package com.tonywww.blackboard.compat.kubejs

import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register

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
}
