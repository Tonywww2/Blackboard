package com.tonywww.blackboard.client

/**
 * Converts the LaTeX subset emitted by the built-in generators (see
 * [com.tonywww.blackboard.builtin.calculus.CalculusExpr]) into ApricityUI-friendly flexbox HTML.
 *
 * Rationale: AUI has no MathML and KaTeX's HTML output relies on browser features AUI's JS engine
 * (Latvian Rhino) and CSS engine don't fully support. But AUI *does* render `display:flex`/
 * `inline-flex`, `border*`, `vertical-align`, and ships default `sub`/`sup` styling. So we render
 * math ourselves as a small tree of flex rows / stacked fractions / radicals and hand the HTML to
 * [com.sighs.apricityui.init.Element.setInnerHTML], which parses it with AUI's own (pure-Java) HTML
 * parser — no KaTeX, no page JS, and **no KubeJS requirement**. See docs/references/apricity-ui.md §6.
 *
 * Pure Kotlin (no MC/AUI imports) so it is unit-testable. Unknown commands degrade to literal text,
 * and plain-text questions (e.g. "88 ÷ 6 = ?") simply render as a text row.
 */
object LatexMath {

    /** Rendered [html] plus an approximate size in `em` units, used by the caller to fit the font. */
    data class Rendered(val html: String, val widthEm: Double, val heightEm: Double)

    fun render(latex: String): Rendered {
        val box = Parser(tokenize(latex)).parseRow()
        return Rendered(box.html, maxOf(box.w, 0.5), maxOf(box.h, 1.0))
    }

    /** A rendered fragment: [html] and its approximate width/height in `em`. */
    private class Box(val html: String, val w: Double, val h: Double)

    // ---- tokenizer ----------------------------------------------------------------------------

    /**
     * Splits LaTeX into tokens: commands (`\frac`, `\,`, ...), structure (`{ } ^ _`), a collapsed
     * space token (` `), and single literal characters.
     */
    private fun tokenize(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> {
                    if (i + 1 < s.length && !s[i + 1].isLetter()) {
                        out.add("\\" + s[i + 1]); i += 2 // control symbol: \, \! \; \{ ...
                    } else {
                        var j = i + 1
                        while (j < s.length && s[j].isLetter()) j++
                        out.add(s.substring(i, j)); i = j // control word: \frac \sqrt \sin ...
                    }
                }
                c == '{' || c == '}' || c == '^' || c == '_' -> { out.add(c.toString()); i++ }
                c == ' ' || c == '\t' || c == '\n' -> {
                    if (out.isEmpty() || out.last() != " ") out.add(" ") // collapse runs
                    i++
                }
                else -> { out.add(c.toString()); i++ }
            }
        }
        return out
    }

    // ---- parser -------------------------------------------------------------------------------

    private class Parser(private val t: List<String>) {
        private var i = 0

        private fun peek(): String? = if (i < t.size) t[i] else null
        private fun next(): String? = if (i < t.size) t[i++] else null

        /** Parse a horizontal run until a closer (`}` / `\right`) or end of input. */
        fun parseRow(): Box {
            val sb = StringBuilder()
            var w = 0.0
            var h = 1.0
            while (true) {
                val p = peek() ?: break
                if (p == "}" || p == "\\right") break
                val atom = parseScripted() ?: break
                sb.append(atom.html)
                w += atom.w
                h = maxOf(h, atom.h)
            }
            return Box("<span class=\"mrow\">$sb</span>", w, h)
        }

        /** An atom plus any trailing `^`/`_` scripts. */
        private fun parseScripted(): Box? {
            var base = parseAtom() ?: return null
            while (true) {
                val p = peek()
                if (p == "^" || p == "_") {
                    next()
                    val script = parseAtom() ?: Box("", 0.0, 0.0)
                    base = applyScript(base, script, p == "^")
                } else {
                    break
                }
            }
            return base
        }

        private fun parseAtom(): Box? {
            val p = peek() ?: return null
            return when (p) {
                "}", "\\right" -> null
                "{" -> { next(); val b = parseRow(); if (peek() == "}") next(); b }
                "^", "_" -> { next(); parseAtom() } // stray script marker: treat next as base
                "\\frac" -> { next(); parseFrac() }
                "\\sqrt" -> { next(); parseSqrt() }
                "\\left" -> { next(); parseDelimited() }
                "\\sin", "\\cos", "\\tan", "\\ln", "\\log", "\\exp", "\\lim" ->
                    { next(); func(p.substring(1)) }
                "\\int" -> { next(); symbol("∫", 0.6, 1.5, "mint") }
                "\\sum" -> { next(); symbol("∑", 0.8, 1.5, "mint") }
                "\\to", "\\rightarrow" -> { next(); symbol("→", 0.9, 1.0, "mop") }
                "\\infty" -> { next(); symbol("∞", 0.8, 1.0, null) }
                "\\cdot" -> { next(); symbol("·", 0.4, 1.0, "mop") }
                "\\times" -> { next(); symbol("×", 0.7, 1.0, "mop") }
                "\\div" -> { next(); symbol("÷", 0.7, 1.0, "mop") }
                "\\pi" -> { next(); symbol("π", 0.6, 1.0, null) }
                "\\," , "\\!", "\\;", "\\:", "\\quad", "\\qquad", " " -> { next(); space() }
                else -> {
                    next()
                    if (p.startsWith("\\")) text(p.substring(1)) // unknown command -> literal name
                    else textChar(p)
                }
            }
        }

        private fun parseFrac(): Box {
            val num = parseAtom() ?: Box("", 0.0, 0.0)
            val den = parseAtom() ?: Box("", 0.0, 0.0)
            val html = "<span class=\"mfrac\">" +
                "<span class=\"mfrac-num\">${num.html}</span>" +
                "<span class=\"mfrac-bar\"></span>" +
                "<span class=\"mfrac-den\">${den.html}</span>" +
                "</span>"
            return Box(html, maxOf(num.w, den.w) + 0.3, num.h + den.h + 0.25)
        }

        private fun parseSqrt(): Box {
            val rad = parseAtom() ?: Box("", 0.0, 0.0)
            val html = "<span class=\"msqrt\">" +
                "<span class=\"msqrt-sign\">√</span>" +
                "<span class=\"msqrt-rad\">${rad.html}</span>" +
                "</span>"
            return Box(html, rad.w + 0.7, rad.h + 0.15)
        }

        /** `\left<d> ... \right<d>` — render the delimiters around the inner row (non-stretchy). */
        private fun parseDelimited(): Box {
            val open = delimText(next()) // delimiter char after \left
            val inner = parseRow()
            if (peek() == "\\right") next()
            val close = delimText(next()) // delimiter char after \right
            val html = "<span class=\"mrow\">" +
                "<span class=\"mopen\">$open</span>${inner.html}<span class=\"mclose\">$close</span>" +
                "</span>"
            return Box(html, inner.w + 0.8, inner.h)
        }

        private fun applyScript(base: Box, script: Box, sup: Boolean): Box {
            val tag = if (sup) "sup" else "sub"
            val html = "<span class=\"matom\">${base.html}<$tag class=\"mscript\">${script.html}</$tag></span>"
            return Box(html, base.w + script.w * 0.72 + 0.05, base.h + script.h * 0.4)
        }

        // ---- leaf builders --------------------------------------------------------------------

        private fun func(name: String): Box =
            Box("<span class=\"mfunc\">${esc(name)}</span>", 0.5 * name.length + 0.1, 1.0)

        private fun symbol(ch: String, w: Double, h: Double, cls: String?): Box {
            val c = if (cls == null) "" else " class=\"$cls\""
            return Box("<span$c>$ch</span>", w, h)
        }

        private fun space(): Box = Box("<span class=\"msp\"></span>", 0.3, 0.0)

        private fun text(s: String): Box =
            Box("<span class=\"mtext\">${esc(s)}</span>", 0.55 * s.length, 1.0)

        private fun textChar(c: String): Box {
            val w = when (c) {
                "+", "-", "=", "<", ">", "±" -> 0.7
                "(", ")", "[", "]", "|", "." -> 0.35
                else -> 0.55
            }
            return Box("<span class=\"mtext\">${esc(c)}</span>", w, 1.0)
        }

        private fun delimText(tok: String?): String = when (tok) {
            null, ".", "\\." -> ""
            "\\{" -> "{"
            "\\}" -> "}"
            else -> esc(tok)
        }
    }

    /** Minimal HTML text escaping for injected content. */
    private fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> sb.append("&quot;")
            else -> sb.append(c)
        }
        return sb.toString()
    }
}
