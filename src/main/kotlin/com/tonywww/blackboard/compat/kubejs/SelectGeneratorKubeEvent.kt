package com.tonywww.blackboard.compat.kubejs

import com.tonywww.blackboard.api.event.SelectGeneratorEvent
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer

/**
 * Server event wrapping the native [SelectGeneratorEvent]. JS can inspect/modify candidates or
 * force a generator; every change writes straight through to the native event.
 *
 * Platform boundary: only the server-event base class name differs (KubeJS 6 `ServerEventJS` vs
 * KubeJS 7 `ServerKubeEvent`); both take a [MinecraftServer].
 */
class SelectGeneratorKubeEvent(server: MinecraftServer, private val native: SelectGeneratorEvent)
//? if forge {
    : dev.latvian.mods.kubejs.server.ServerEventJS(server)
//?} else {
/*: dev.latvian.mods.kubejs.server.ServerKubeEvent(server)
*///?}
{
    /** Candidate generator ids, in current order. */
    fun getCandidateIds(): List<ResourceLocation> = native.candidates.map { it.generator.id }

    /** Override the selection weight of the candidate with [id] (no-op if absent). */
    fun setWeight(id: ResourceLocation, weight: Int) {
        native.candidates.firstOrNull { it.generator.id == id }?.weight = weight
    }

    /** Remove the candidate with [id] from the pool. */
    fun removeCandidate(id: ResourceLocation) {
        native.candidates.removeIf { it.generator.id == id }
    }

    /** Force a specific generator by [id], skipping weighted random; no-op if unknown. */
    fun force(id: ResourceLocation) {
        BlackboardRegistries.QUESTION_GENERATORS.get(id)?.let { native.forced = it }
    }
}
