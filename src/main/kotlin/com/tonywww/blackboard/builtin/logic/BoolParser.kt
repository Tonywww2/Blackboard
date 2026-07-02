package com.tonywww.blackboard.builtin.logic

/**
 * 命题逻辑表达式解析器（字符串 → [BoolExpr]），供化简题判题把玩家输入解析回 AST。
 *
 * 宽松接受多种写法：
 *  - 变量：单个字母（大小写不敏感，归一为小写）；常量：`T`/`F`、`1`/`0`、`true`/`false`。
 *  - 否定：`!` `~` `¬` `NOT`；与：`&` `&&` `∧` `*` `AND`；或：`|` `||` `∨` `+` `OR`；
 *    异或：`^` `⊕` `XOR`；蕴含：`->` `→` `IMP`；等价：`<->` `↔` `IFF`；括号 `()`。
 *  - 优先级（高→低）：`¬` > `∧` > `⊕` > `∨` > `→` > `↔`；同级左结合。
 *
 * 任何词法/语法错误（未知记号、缺操作数、括号不匹配、多余记号）→ 返回 `null`（判题据此回 Invalid）。
 * 纯 Kotlin、无平台依赖，可单测。
 */
object BoolParser {

    fun parse(s: String): BoolExpr? {
        val tokens = tokenize(s) ?: return null
        if (tokens.isEmpty()) return null
        val p = Parser(tokens)
        val e = p.parseExpr() ?: return null
        return if (p.atEnd()) e else null
    }

    // ---- 记号 ----

    private sealed interface Tok
    private data class TVar(val c: Char) : Tok
    private data class TConst(val v: Boolean) : Tok
    private data class TOp(val op: BinOp) : Tok
    private data object TNot : Tok
    private data object TLParen : Tok
    private data object TRParen : Tok

    private fun tokenize(s: String): List<Tok>? {
        val out = ArrayList<Tok>()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out.add(TLParen); i++ }
                c == ')' -> { out.add(TRParen); i++ }
                c == '!' || c == '~' || c == '\u00ac' -> { out.add(TNot); i++ }
                c == '&' || c == '\u2227' || c == '*' -> { out.add(TOp(BinOp.AND)); i++; if (i < n && s[i] == '&') i++ }
                c == '|' || c == '\u2228' || c == '+' -> { out.add(TOp(BinOp.OR)); i++; if (i < n && s[i] == '|') i++ }
                c == '^' || c == '\u2295' -> { out.add(TOp(BinOp.XOR)); i++ }
                c == '\u2194' -> { out.add(TOp(BinOp.IFF)); i++ }
                c == '\u2192' -> { out.add(TOp(BinOp.IMP)); i++ }
                c == '1' -> { out.add(TConst(true)); i++ }
                c == '0' -> { out.add(TConst(false)); i++ }
                c == '<' -> {
                    if (i + 2 < n && s[i + 1] == '-' && s[i + 2] == '>') { out.add(TOp(BinOp.IFF)); i += 3 } else return null
                }
                c == '-' -> {
                    if (i + 1 < n && s[i + 1] == '>') { out.add(TOp(BinOp.IMP)); i += 2 } else return null
                }
                c.isLetter() -> {
                    val start = i
                    while (i < n && s[i].isLetter()) i++
                    out.add(keyword(s.substring(start, i)) ?: return null)
                }
                else -> return null
            }
        }
        return out
    }

    /** 字母序列 → 关键字记号；单个非关键字字母 → 变量（归一小写）；未知多字母词 → null。 */
    private fun keyword(w: String): Tok? = when (w.lowercase()) {
        "and" -> TOp(BinOp.AND)
        "or" -> TOp(BinOp.OR)
        "xor" -> TOp(BinOp.XOR)
        "imp" -> TOp(BinOp.IMP)
        "iff" -> TOp(BinOp.IFF)
        "not" -> TNot
        "true", "t" -> TConst(true)
        "false", "f" -> TConst(false)
        else -> if (w.length == 1) TVar(w[0].lowercaseChar()) else null
    }

    // ---- 递归下降（优先级低→高：IFF < IMP < OR < XOR < AND < NOT < atom；同级左结合） ----

    private val LEVELS = listOf(BinOp.IFF, BinOp.IMP, BinOp.OR, BinOp.XOR, BinOp.AND)

    private class Parser(val tokens: List<Tok>) {
        private var pos = 0

        fun atEnd() = pos >= tokens.size
        private fun peek(): Tok? = tokens.getOrNull(pos)

        fun parseExpr(): BoolExpr? = parseBin(0)

        private fun parseBin(level: Int): BoolExpr? {
            if (level >= LEVELS.size) return parseNot()
            var left = parseBin(level + 1) ?: return null
            val op = LEVELS[level]
            while (peek() == TOp(op)) {
                pos++
                val right = parseBin(level + 1) ?: return null
                left = Bin(op, left, right)
            }
            return left
        }

        private fun parseNot(): BoolExpr? {
            if (peek() == TNot) {
                pos++
                return parseNot()?.let { Not(it) }
            }
            return parseAtom()
        }

        private fun parseAtom(): BoolExpr? = when (val t = peek()) {
            is TVar -> { pos++; Var(t.c) }
            is TConst -> { pos++; Lit(t.v) }
            TLParen -> {
                pos++
                val e = parseBin(0) ?: return null
                if (peek() != TRParen) return null
                pos++
                e
            }
            else -> null
        }
    }
}
