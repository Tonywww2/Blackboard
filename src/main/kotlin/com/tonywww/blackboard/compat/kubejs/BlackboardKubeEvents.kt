package com.tonywww.blackboard.compat.kubejs

import dev.latvian.mods.kubejs.event.EventGroup
import dev.latvian.mods.kubejs.event.EventHandler

/**
 * KubeJS event group exposing Blackboard's three scriptable events.
 *
 * The group/handler types share the same package on KubeJS 6 (1.20.1) and KubeJS 7 (1.21.1), so
 * this file needs no platform branch — the per-version event base classes are isolated inside the
 * individual `*KubeEvent` classes instead.
 *
 * JS usage:
 * ```js
 * BlackboardEvents.registerGenerators(event => event.register(generator))
 * BlackboardEvents.selectGenerator(event => event.force("blackboard:add"))
 * BlackboardEvents.reward(event => event.setLootTable("blackboard:rewards/default"))
 * ```
 */
object BlackboardKubeEvents {
    val GROUP: EventGroup = EventGroup.of("BlackboardEvents")

    /** Startup: contribute [com.tonywww.blackboard.api.question.QuestionGenerator]s. */
    val REGISTER_GENERATORS: EventHandler =
        GROUP.startup("registerGenerators") { RegisterGeneratorsKubeEvent::class.java }

    /** Server: inspect/modify candidates or force a generator before selection. */
    val SELECT_GENERATOR: EventHandler =
        GROUP.server("selectGenerator") { SelectGeneratorKubeEvent::class.java }

    /** Server: customize the reward (loot table / extra drops). */
    val REWARD: EventHandler =
        GROUP.server("reward") { RewardKubeEvent::class.java }
}
