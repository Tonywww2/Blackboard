package com.tonywww.blackboard.client

import net.minecraft.client.Minecraft
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * Rasterizes a blackboard question (LaTeX, or plain arithmetic like `88 ÷ 6 = ?`) to a
 * white-on-transparent PNG with **JLaTeXMath** (pure-Java LaTeX, no page JS or KubeJS) and
 * writes it under `<gamedir>/apricity/blackboard/gen/<hash>.png`, where AUI's `<img>` loader
 * ([com.sighs.apricityui.instance.Loader]/`ClientLoader.getResourceStream`) can pick it up.
 *
 * Rendering runs on a single daemon worker thread (off the render thread, and single-threaded to keep
 * JLaTeXMath's static font setup safe); [request]'s `onReady` is delivered on the MC client thread with
 * the AUI-relative `src` and the natural pixel size, so the caller can size the `<img>` to fit.
 * Files are content-addressed, so identical questions share one texture and re-rendered boards reuse it.
 */
object LatexImageRenderer {

    /** A rendered question: AUI-relative `src` (relative to `blackboard/board.html`) and natural size. */
    data class Result(val src: String, val widthPx: Int, val heightPx: Int)

    private val logger = LoggerFactory.getLogger("Blackboard/Latex")

    private const val POINT_SIZE = 96f
    private const val INSET = 6
    private val CHALK = Color(0xF4, 0xFF, 0xF7)

    /** Subdirectory under `apricity/blackboard/` for generated images; also the `<img src>` prefix. */
    private const val GEN_DIR = "gen"

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "blackboard-latex").apply { isDaemon = true }
    }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Result>()
    private val pending = HashMap<String, MutableList<(Result) -> Unit>>()

    /**
     * Ensure a PNG exists for [content] and deliver its [Result] to [onReady] on the MC client thread.
     * Cached results fire immediately (on the calling thread); otherwise the render is queued and all
     * callbacks for the same content are fired together once the image is written.
     */
    fun request(content: String, onReady: (Result) -> Unit) {
        val key = hash(content)
        cache[key]?.let { onReady(it); return }

        val shouldSubmit: Boolean
        synchronized(pending) {
            val callbacks = pending.getOrPut(key) { mutableListOf() }
            shouldSubmit = callbacks.isEmpty()
            callbacks.add(onReady)
        }
        if (!shouldSubmit) return

        worker.execute {
            val result = try {
                renderToFile(content, key)
            } catch (t: Throwable) {
                logger.error("Failed to render question <{}>", content, t)
                null
            }
            val callbacks: List<(Result) -> Unit>
            synchronized(pending) { callbacks = pending.remove(key) ?: emptyList() }
            if (result != null) {
                cache[key] = result
                Minecraft.getInstance().execute { callbacks.forEach { it(result) } }
            }
        }
    }

    private fun renderToFile(content: String, key: String): Result {
        val dir = Minecraft.getInstance().gameDirectory.resolve("apricity/blackboard/$GEN_DIR")
        dir.mkdirs()
        val file = File(dir, "$key.png")
        // Cross-session disk cache: an identical PNG (content+size hash) may already be on disk from a
        // previous run. Reuse it by reading only its header dimensions, skipping the JLaTeXMath render.
        if (file.isFile) {
            readPngSize(file)?.let { return Result("$GEN_DIR/$key.png", it.first, it.second) }
        }
        val image = renderImage(content)
        ImageIO.write(image, "png", file)
        return Result("$GEN_DIR/$key.png", image.width, image.height)
    }

    /** Read a PNG's pixel dimensions from its header (no full decode), or null if unreadable. */
    private fun readPngSize(file: File): Pair<Int, Int>? =
        try {
            ImageIO.createImageInputStream(file)?.use { iis ->
                val readers = ImageIO.getImageReaders(iis)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    try {
                        reader.input = iis
                        reader.getWidth(0) to reader.getHeight(0)
                    } finally {
                        reader.dispose()
                    }
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }

    /** LaTeX first (via JLaTeXMath); on any parse/render failure fall back to a plain-text raster. */
    private fun renderImage(content: String): BufferedImage =
        try {
            renderLatex(preprocess(content))
        } catch (t: Throwable) {
            logger.warn("JLaTeXMath failed for <{}>, falling back to plain text", content, t)
            renderPlainText(content)
        }

    private fun renderLatex(latex: String): BufferedImage {
        val icon = TeXFormula(latex).createTeXIcon(TeXConstants.STYLE_DISPLAY, POINT_SIZE)
        icon.insets = Insets(INSET, INSET, INSET, INSET)
        val w = maxOf(1, icon.iconWidth)
        val h = maxOf(1, icon.iconHeight)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        icon.setForeground(CHALK)
        icon.paintIcon(null, g, 0, 0)
        g.dispose()
        return image
    }

    private fun renderPlainText(text: String): BufferedImage {
        val font = Font(Font.SANS_SERIF, Font.PLAIN, 32)
        val metrics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
            .apply { this.font = font }.fontMetrics
        val w = maxOf(1, metrics.stringWidth(text) + INSET * 2)
        val h = maxOf(1, metrics.height + INSET * 2)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = font
        g.color = CHALK
        g.drawString(text, INSET, INSET + metrics.ascent)
        g.dispose()
        return image
    }

    /**
     * Map the few Unicode math operators the arithmetic generators emit (e.g. `÷ × · −`) to LaTeX so
     * they render in math mode. LaTeX questions never contain these raw glyphs, so this is a no-op there.
     */
    private fun preprocess(content: String): String {
        val sb = StringBuilder(content.length + 8)
        for (c in content) {
            when (c) {
                '÷' -> sb.append(" \\div ")
                '×' -> sb.append(" \\times ")
                '·' -> sb.append(" \\cdot ")
                '−' -> sb.append('-')
                '≤' -> sb.append(" \\le ")
                '≥' -> sb.append(" \\ge ")
                '≠' -> sb.append(" \\ne ")
                '≈' -> sb.append(" \\approx ")
                'π' -> sb.append(" \\pi ")
                '∞' -> sb.append(" \\infty ")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun hash(content: String): String {
        // Seed the hash with the render size so changing POINT_SIZE yields new files (never reuses a
        // stale lower-resolution PNG) and different resolutions can coexist on disk.
        val seed = "${POINT_SIZE.toInt()}:$content"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(16) { for (i in 0 until 8) append("%02x".format(digest[i])) }
    }
}
