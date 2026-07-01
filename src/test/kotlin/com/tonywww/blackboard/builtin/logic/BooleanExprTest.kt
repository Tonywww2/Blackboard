package com.tonywww.blackboard.builtin.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BooleanExprTest {

    private val t = Lit(true)
    private val f = Lit(false)

    @Test
    fun `operators evaluate per truth table`() {
        assertTrue(Bin(BinOp.AND, t, t).eval())
        assertFalse(Bin(BinOp.AND, t, f).eval())
        assertTrue(Bin(BinOp.OR, t, f).eval())
        assertFalse(Bin(BinOp.OR, f, f).eval())
        assertTrue(Bin(BinOp.XOR, t, f).eval())
        assertFalse(Bin(BinOp.XOR, t, t).eval())
        // 蕴含 T→F 为假，其余为真
        assertFalse(Bin(BinOp.IMP, t, f).eval())
        assertTrue(Bin(BinOp.IMP, f, t).eval())
        assertTrue(Bin(BinOp.IMP, f, f).eval())
        // 双条件
        assertTrue(Bin(BinOp.IFF, t, t).eval())
        assertFalse(Bin(BinOp.IFF, t, f).eval())
        // 否定
        assertFalse(Not(t).eval())
        assertTrue(Not(f).eval())
    }

    @Test
    fun `variables evaluate under assignment and vars are collected`() {
        val expr = Bin(BinOp.AND, Var('p'), Not(Var('q'))) // p ∧ ¬q
        assertTrue(expr.eval(mapOf('p' to true, 'q' to false)))
        assertFalse(expr.eval(mapOf('p' to true, 'q' to true)))
        assertEquals(setOf('p', 'q'), expr.vars())
    }

    @Test
    fun `binary children parenthesized, atoms and not are not`() {
        val expr = Bin(BinOp.OR, Bin(BinOp.AND, Var('p'), Var('q')), Not(Var('r'))) // (p∧q) ∨ ¬r
        val latex = expr.toLatex()
        assertTrue(latex.contains("\\left("), "binary child should be parenthesized: $latex")
        assertTrue(latex.contains("\\lnot r"), "not-atom should not be parenthesized: $latex")
        assertEquals("(p AND q) OR NOT r", expr.toInfix())
    }
}
