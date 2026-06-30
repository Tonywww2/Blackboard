package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.platform.PlatformComponents
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/*
 * NBT serialization of a [Question] (the sub-compound stored on a blackboard block entity).
 *
 * Keys (internal-core-api §9):
 *   Generator : String   -> generatorId
 *   Content   : String   -> content, encoded via PlatformComponents (platform boundary, C6)
 *   Prompt    : String?  -> optional prompt, encoded via PlatformComponents
 *   Data      : Compound -> generator-private data, stored verbatim
 *
 * The `registries` provider is threaded into PlatformComponents for Component (de)serialization;
 * it is required on 1.21.x and ignored on 1.20.1, so this file needs no per-loader branch.
 */

private const val KEY_GENERATOR = "Generator"
private const val KEY_CONTENT = "Content"
private const val KEY_PROMPT = "Prompt"
private const val KEY_DATA = "Data"

/** Full serialization for server-side persistence (`saveAdditional`): includes `Prompt` and `Data`. */
fun Question.toNbt(registries: HolderLookup.Provider): CompoundTag = CompoundTag().apply {
    putString(KEY_GENERATOR, generatorId.toString())
    putString(KEY_CONTENT, PlatformComponents.toJson(content, registries))
    prompt?.let { putString(KEY_PROMPT, PlatformComponents.toJson(it, registries)) }
    put(KEY_DATA, data.copy())
}

/**
 * Client-sync serialization (`getUpdateTag`): only `Generator` + `Content`.
 *
 * Deliberately omits `Data` (which holds the answer) and `Prompt`, so the answer is never sent to
 * clients — they only receive what is needed to render the board.
 */
fun Question.toClientNbt(registries: HolderLookup.Provider): CompoundTag = CompoundTag().apply {
    putString(KEY_GENERATOR, generatorId.toString())
    putString(KEY_CONTENT, PlatformComponents.toJson(content, registries))
}

/**
 * Reconstructs a [Question] from [tag].
 *
 * Returns `null` if required keys are missing or malformed (unparseable generator id, invalid
 * component JSON), so callers fail safe on corrupt saves or unexpected packets. A missing `Data`
 * compound (e.g. a client-sync tag) yields an empty data compound.
 */
fun questionFromNbt(tag: CompoundTag, registries: HolderLookup.Provider): Question? {
    if (!tag.contains(KEY_GENERATOR) || !tag.contains(KEY_CONTENT)) return null
    val generatorId = ResourceLocation.tryParse(tag.getString(KEY_GENERATOR)) ?: return null
    val content = PlatformComponents.fromJson(tag.getString(KEY_CONTENT), registries) ?: return null
    val prompt =
        if (tag.contains(KEY_PROMPT)) PlatformComponents.fromJson(tag.getString(KEY_PROMPT), registries) else null
    return QuestionImpl(generatorId, content, tag.getCompound(KEY_DATA).copy(), prompt)
}
