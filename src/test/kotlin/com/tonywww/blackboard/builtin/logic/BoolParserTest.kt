package com.tonywww.blackboard.builtin.logic

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 布尔表达式解析器：常量/变量、各算符的符号与词变体、优先级、`toInfix` 往返、非法输入拒绝。 */
class BoolParserTest {

    private fun p(s: String) = BoolParser.parse(s)

    private fun assertEquiv(expected: BoolExpr, input: String) {
        val parsed = p(input)
        assertNotNull(parsed, "解析失败: <$input>")
        assertTrue(equivalent(expected, parsed!!), "不等价: <$input> -> ${parsed.toInfix()}")
    }

    @Test
    fun `parses constants and variables`() {
        for (s in listOf("T", "true", "1")) assertEquiv(Lit(true), s)
        for (s in listOf("F", "false", "0")) assertEquiv(Lit(false), s)
        assertEquiv(Var('p'), "p")
        assertEquiv(Var('p'), "P") // 大小写归一
    }

    @Test
    fun `accepts symbol and word variants for each operator`() {
        val and = Bin(BinOp.AND, Var('p'), Var('q'))
        for (s in listOf("p&q", "p && q", "p \u2227 q", "p*q", "p AND q", "p and q")) assertEquiv(and, s)
        val or = Bin(BinOp.OR, Var('p'), Var('q'))
        for (s in listOf("p|q", "p || q", "p \u2228 q", "p+q", "p OR q")) assertEquiv(or, s)
        for (s in listOf("!p", "~p", "\u00acp", "NOT p")) assertEquiv(Not(Var('p')), s)
        for (s in listOf("p^q", "p \u2295 q", "p XOR q")) assertEquiv(Bin(BinOp.XOR, Var('p'), Var('q')), s)
        for (s in listOf("p->q", "p \u2192 q", "p IMP q")) assertEquiv(Bin(BinOp.IMP, Var('p'), Var('q')), s)
        for (s in listOf("p<->q", "p \u2194 q", "p IFF q")) assertEquiv(Bin(BinOp.IFF, Var('p'), Var('q')), s)
    }

    @Test
    fun `respects precedence (not gt and gt or) and parentheses`() {
        assertEquiv(Bin(BinOp.OR, Var('p'), Bin(BinOp.AND, Var('q'), Var('r'))), "p | q & r")
        assertEquiv(Bin(BinOp.AND, Not(Var('p')), Var('q')), "!p & q")
        assertEquiv(Bin(BinOp.AND, Bin(BinOp.OR, Var('p'), Var('q')), Var('r')), "(p|q)&r")
    }

    @Test
    fun `round-trips toInfix output back to an equivalent tree`() {
        val exprs = listOf<BoolExpr>(
            Var('p'),
            Not(Var('q')),
            Bin(BinOp.AND, Var('p'), Not(Var('q'))),
            Bin(BinOp.OR, Bin(BinOp.AND, Var('p'), Var('q')), Var('r')),
            Bin(BinOp.IFF, Bin(BinOp.IMP, Var('p'), Var('q')), Bin(BinOp.XOR, Var('r'), Var('s'))),
            Not(Bin(BinOp.OR, Var('p'), Bin(BinOp.AND, Var('q'), Not(Var('r'))))),
            Lit(true),
            Lit(false),
        )
        for (e in exprs) {
            val back = p(e.toInfix())
            assertNotNull(back, "round-trip 解析失败: ${e.toInfix()}")
            assertTrue(equivalent(e, back!!), "round-trip 不等价: ${e.toInfix()}")
        }
    }

    @Test
    fun `rejects malformed input`() {
        for (s in listOf("", "   ", "p &", "& p", "(p", "p)", "p q", "@", "foo", "p ! q", "->p")) {
            assertNull(p(s), "应解析失败: <$s>")
        }
    }
}
