package com.tonywww.blackboard.client

import com.sighs.apricityui.ApricityUI
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.init.Event
import com.tonywww.blackboard.answer.AnswerScreenState
import com.tonywww.blackboard.content.BlackboardBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
//? if forge {
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.common.MinecraftForge
//?} else {
/*import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge
*///?}

/**
 * Client-side wiring for the chalk answer screen (P: answer UX). Touches ApricityUI + client classes, so
 * it is only registered from [BlackboardClient.init] **after** the `isLoaded("apricityui")` guard.
 *
 * When any screen finishes initializing, if the answer template's [com.sighs.apricityui.init.Document] is
 * present (i.e. our screen is the one that opened) we:
 *  - show the board id and rasterize the question to a PNG via [LatexImageRenderer] into `<img id=content>`
 *    (same path as the in-world renderer);
 *  - bind a `click` handler on `<input id=submit>` that reads `<textarea id=answer>` and sends the answer
 *    through chat as `!ans <boardId> <answer>` (reusing the existing chat pipeline — no custom packet),
 *    then closes the screen.
 *
 * `ScreenEvent.Init.Post` also fires on window resize (AUI rebuilds the Document then), so re-injecting is
 * idempotent: attributes are re-set and the click listener is re-bound on the fresh elements.
 */
object AnswerScreenInjector {

    fun register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.addListener { _: ScreenEvent.Init.Post -> onScreenInit() }
        //?} else {
        /*NeoForge.EVENT_BUS.addListener { _: ScreenEvent.Init.Post -> onScreenInit() }
        *///?}
    }

    /** Client entry: ask AUI to open the (server-authoritative) answer screen. */
    fun open() {
        ApricityUI.openScreen(AnswerScreenState.TEMPLATE)
    }

    private fun onScreenInit() {
        val doc = ApricityUI.getDocument(AnswerScreenState.TEMPLATE).firstOrNull() ?: return
        val pos = AnswerScreenState.pendingPos ?: return
        val be = Minecraft.getInstance().level?.getBlockEntity(pos) as? BlackboardBlockEntity ?: return
        val boardId = be.boardId

        doc.getElementById(ID_BOARD)?.let { setText(it, "#$boardId") }
        doc.getElementById(ID_ANSWER)?.setAttribute("placeholder", I18n.get("gui.blackboard.answer_placeholder"))

        val content = be.question?.content?.string
        val img = doc.getElementById(ID_CONTENT)
        if (img != null && !content.isNullOrBlank()) {
            LatexImageRenderer.request(content) { result -> applyImage(img, result) }
        }

        doc.getElementById(ID_SUBMIT)?.let { button ->
            button.setValue(I18n.get("gui.blackboard.submit"))
            button.addEventListener("click") { _: Event -> submit(boardId) }
        }
    }

    /** Read the input box and send the answer through chat, then close the screen. */
    private fun submit(boardId: String) {
        val doc = ApricityUI.getDocument(AnswerScreenState.TEMPLATE).firstOrNull() ?: return
        val field = doc.getElementById(ID_ANSWER)
        val answer = (field?.value ?: field?.innerText ?: "").trim()
        if (answer.isEmpty()) return
        Minecraft.getInstance().player?.connection?.sendChat("!ans $boardId $answer")
        ApricityUI.closeScreen()
    }

    /** Point [img] at the rendered PNG and size it (preserving aspect) to fit the content area. */
    private fun applyImage(img: Element, result: LatexImageRenderer.Result) {
        val scale = minOf(CONTENT_W / result.widthPx, CONTENT_H / result.heightPx, 1.0)
        val w = (result.widthPx * scale).toInt().coerceAtLeast(1)
        val h = (result.heightPx * scale).toInt().coerceAtLeast(1)
        img.setAttribute("src", result.src)
        img.setAttribute("style", "width:${w}px;height:${h}px")
    }

    private fun setText(element: Element, value: String) {
        //? if forge {
        element.setTextContent(value)
        //?} else {
        /*if (element.innerText == value) return
        element.innerText = value
        val renderer = element.getRenderer()
        renderer.text.clear()
        renderer.wrappedText.clear()
        element.invalidateStyle()
        *///?}
    }

    private const val ID_BOARD = "board-id"
    private const val ID_CONTENT = "content"
    private const val ID_ANSWER = "answer"
    private const val ID_SUBMIT = "submit"
    private const val CONTENT_W = 300.0 // usable content width in AUI px (panel minus padding)
    private const val CONTENT_H = 102.0 // usable content height in AUI px
}
