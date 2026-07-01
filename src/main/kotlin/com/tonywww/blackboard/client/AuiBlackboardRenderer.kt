package com.tonywww.blackboard.client

import com.sighs.apricityui.ApricityUI
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.instance.WorldWindow
import com.tonywww.blackboard.api.render.BlackboardRenderer
import com.tonywww.blackboard.api.render.RenderContext
import com.tonywww.blackboard.content.BlackboardBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

/**
 * ApricityUI-backed [BlackboardRenderer] (P8-A).
 *
 * Shows the board's [RenderContext.boardId] together with the question on an in-world panel in front
 * of the blackboard. The question [net.minecraft.network.chat.Component] is flattened to a string via
 * [net.minecraft.network.chat.Component.getString] and rasterized to a white-on-transparent PNG by
 * [LatexImageRenderer] (pure-Java JLaTeXMath, no page JS or KubeJS). The PNG is written
 * under the game dir and shown through the template's `<img id="content">`, sized to fit the panel.
 * See docs/references/apricity-ui.md §6.
 *
 * Client-only and only instantiated when ApricityUI is installed (guarded by [BlackboardClient]).
 * One [WorldWindow] is kept per block position and updated in place when the question changes.
 *
 * AUI API confirmed against sources: `ApricityUI.createWorldWindow(path, Vec3, width, height,
 * maxDistance)`, `WorldWindow.document`/`setRotation`, `Document.getElementById`, `Element.setAttribute`
 * (auto re-parses inline style + relayouts), and the per-loader text setter (Forge
 * `Element.setTextContent` vs NeoForge `Element.innerText`).
 */
class AuiBlackboardRenderer : BlackboardRenderer {
    private val windows = HashMap<BlockPos, WorldWindow>()

    override fun render(context: RenderContext) {
        val pos = context.pos.immutable()
        val window = windows.getOrPut(pos) { createWindow(context, pos) }
        window.document.getElementById(ID_BOARD)?.let { setText(it, context.boardId.ifEmpty { "-" }) }
        val img = window.document.getElementById(ID_CONTENT) ?: return
        val content = context.content.string
        if (content.isBlank()) {
            img.setAttribute("src", "")
            return
        }
        // Rasterize the question to a PNG off-thread (JLaTeXMath); when ready, point the <img> at it and
        // size it to fit the panel. AUI's Img polls `src` each frame, so the swap is picked up live.
        LatexImageRenderer.request(content) { result -> applyImage(img, result) }
    }

    override fun remove(pos: BlockPos) {
        windows.remove(pos.immutable())?.let { ApricityUI.removeWorldWindow(it) }
    }

    private fun createWindow(context: RenderContext, pos: BlockPos): WorldWindow {
        val facing = facingOf(context)
        // Align the panel to the static model's board rectangle (model x[-16,32] y[16,48] z[13,15] for
        // facing=north): 3 blocks wide x 2 tall, vertical centre 2 blocks above the block base, sitting
        // just in front of the writing face. WIDTH/HEIGHT are AUI px at the default 0.02 scale (50px/block).
        val position = Vec3(
            pos.x + 0.5 + facing.stepX * ALONG_NORMAL,
            pos.y + BOARD_CENTER_Y,
            pos.z + 0.5 + facing.stepZ * ALONG_NORMAL,
        )
        val window = ApricityUI.createWorldWindow(TEMPLATE, position, WIDTH, HEIGHT, MAX_DISTANCE)
        window.setRotation(yRotFor(facing), 0f)
        return window
    }

    private fun facingOf(context: RenderContext): Direction {
        val state = context.blockState
        return if (state.hasProperty(BlackboardBlock.FACING)) state.getValue(BlackboardBlock.FACING) else Direction.NORTH
    }

    /** Rotate the panel so its face points outward along [facing] (derived from `WorldWindow.render`). */
    private fun yRotFor(facing: Direction): Float = when (facing) {
        Direction.SOUTH -> 180f
        Direction.WEST -> 270f
        Direction.EAST -> 90f
        else -> 0f // NORTH / non-horizontal
    }

    private fun setText(element: Element, value: String) {
        //? if forge {
        element.setTextContent(value)
        //?} else {
        /*if (element.innerText == value) return
        // AUI 1.1.2 无 setTextContent。文本变更照搬 Element.setAttribute("value", …) 的失效流程：
        // 写字段 + 清 text/wrappedText 渲染缓存 + invalidateStyle()（内部 requestStyleRecalc 触发重排）。
        // 切勿用 document.refresh()——它会从 board.html 整树重新解析、重建 DOM，反而抹掉刚写入的文本。
        // 另注：1.1.2 的 <div> 不绘制 innerText（Div.drawPhase 未调用基类 drawInnerText），故模板里 board-id 用 <span>。
        element.innerText = value
        val renderer = element.getRenderer()
        renderer.text.clear()
        renderer.wrappedText.clear()
        element.invalidateStyle()
        *///?}
    }

    /** Point [img] at the rendered PNG and size it (preserving aspect) to fit the content area. */
    private fun applyImage(img: Element, result: LatexImageRenderer.Result) {
        val scale = minOf(CONTENT_W / result.widthPx, CONTENT_H / result.heightPx, 1.0)
        val w = (result.widthPx * scale).toInt().coerceAtLeast(1)
        val h = (result.heightPx * scale).toInt().coerceAtLeast(1)
        // setAttribute re-parses inline style and marks the element for relayout (see Element.java).
        img.setAttribute("src", result.src)
        img.setAttribute("style", "width:${w}px;height:${h}px")
    }

    private companion object {
        const val TEMPLATE = "blackboard/board.html"
        const val ID_BOARD = "board-id"
        const val ID_CONTENT = "content"
        const val WIDTH = 150f // 3 blocks at the default 0.02 scale (50px = 1 block)
        const val HEIGHT = 100f // 2 blocks
        const val MAX_DISTANCE = 16
        const val BOARD_CENTER_Y = 2.0 // board vertical centre, in blocks above the block's base corner
        const val ALONG_NORMAL = -0.3125 // panel-centre offset from block centre along the facing normal (less negative = further back / away from viewer)
        const val CONTENT_W = 134.0 // usable content width in px (panel minus horizontal padding)
        const val CONTENT_H = 72.0 // usable content height in px (panel minus board-id and padding)
    }
}
