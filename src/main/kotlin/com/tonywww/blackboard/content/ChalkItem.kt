package com.tonywww.blackboard.content

import com.tonywww.blackboard.answer.AnswerScreenState
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * Chalk: right-clicking a blackboard opens the ApricityUI answer screen (question image on top, a big
 * input box + submit button below). Submitting sends the answer through chat as `!ans <boardId> <answer>`,
 * reusing the existing chat answer pipeline (no custom networking).
 *
 * The answer screen is a container-bound AUI screen (the only kind present on both loaders — NeoForge
 * 1.1.2 has no client-only `ApricityScreen`). AUI's `ApricityUI.openScreen(path)` already runs the
 * server-authoritative open handshake from the client, so [useOn] acts only client-side (records the
 * clicked board + asks AUI to open); it is a no-op without ApricityUI (see [AnswerScreenState.openOnClient]).
 */
class ChalkItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val be = level.getBlockEntity(context.clickedPos) as? BlackboardBlockEntity ?: return InteractionResult.PASS
        if (be.question == null) return InteractionResult.PASS
        if (level.isClientSide) AnswerScreenState.openOnClient(context.clickedPos)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }
}
