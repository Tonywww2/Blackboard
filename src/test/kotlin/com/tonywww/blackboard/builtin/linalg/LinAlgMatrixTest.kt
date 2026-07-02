package com.tonywww.blackboard.builtin.linalg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 整数矩阵核心运算：转置 / 乘法 / 单位阵 / 错切阵及其逆 / LaTeX / 答案串 / 点积。 */
class LinAlgMatrixTest {

    @Test
    fun `transpose swaps rows and cols`() {
        val m = IntMatrix(listOf(listOf(1, 2, 3), listOf(4, 5, 6)))
        val t = m.transpose()
        assertEquals(3, t.rows)
        assertEquals(2, t.cols)
        assertEquals(IntMatrix(listOf(listOf(1, 4), listOf(2, 5), listOf(3, 6))), t)
        assertEquals(m, t.transpose())
    }

    @Test
    fun `matrix multiplication`() {
        val a = IntMatrix(listOf(listOf(1, 2), listOf(3, 4)))
        val b = IntMatrix(listOf(listOf(5, 6), listOf(7, 8)))
        assertEquals(IntMatrix(listOf(listOf(19, 22), listOf(43, 50))), a * b)
    }

    @Test
    fun `matrix times column vector`() {
        val a = IntMatrix(listOf(listOf(1, 2), listOf(3, 4)))
        val v = IntMatrix(listOf(listOf(5), listOf(6)))
        assertEquals(IntMatrix(listOf(listOf(17), listOf(39))), a * v)
    }

    @Test
    fun `shear and its inverse multiply to identity`() {
        val n = 3
        for (m in listOf(1, -1, 2, -2)) {
            val e = IntMatrix.shear(n, 0, 1, m)
            val eInv = IntMatrix.shear(n, 0, 1, -m)
            assertEquals(IntMatrix.identity(n), e * eInv)
            assertEquals(IntMatrix.identity(n), eInv * e)
        }
    }

    @Test
    fun `latex is pmatrix and answer string has double brackets`() {
        val m = IntMatrix(listOf(listOf(1, -2), listOf(3, 4)))
        assertTrue(m.toLatex().startsWith("\\begin{pmatrix}"))
        assertTrue(m.toLatex().endsWith("\\end{pmatrix}"))
        assertEquals("[[1,-2],[3,4]]", m.toAnswerString())
        // 列向量答案串
        assertEquals("[[5],[6]]", IntMatrix(listOf(listOf(5), listOf(6))).toAnswerString())
    }

    @Test
    fun `dot product`() {
        assertEquals(32, dot(listOf(1, 2, 3), listOf(4, 5, 6)))
        assertEquals(0, dot(listOf(1, -1), listOf(2, 2)))
    }

    @Test
    fun `maxAbs`() {
        assertEquals(7, IntMatrix(listOf(listOf(1, -7), listOf(3, 4))).maxAbs())
    }

    @Test
    fun `diagonalOnes and isTriangular`() {
        assertEquals(2, IntMatrix(listOf(listOf(1, 5), listOf(0, 1))).diagonalOnes())
        assertEquals(0, IntMatrix(listOf(listOf(2, 5), listOf(3, 2))).diagonalOnes())
        assertTrue(IntMatrix(listOf(listOf(1, 5), listOf(0, 1))).isTriangular()) // 上三角
        assertTrue(IntMatrix(listOf(listOf(2, 0), listOf(3, 4))).isTriangular()) // 下三角
        assertFalse(IntMatrix(listOf(listOf(2, 3), listOf(1, 2))).isTriangular()) // 满阵
    }
}
