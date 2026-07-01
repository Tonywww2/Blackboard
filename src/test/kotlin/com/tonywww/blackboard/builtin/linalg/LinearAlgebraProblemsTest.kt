package com.tonywww.blackboard.builtin.linalg

import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 出题逻辑自洽：各题型的标准答案与其操作数一致；矩阵规模随难度增大。 */
class LinearAlgebraProblemsTest {

    @Test
    fun `dot product answer matches operands across difficulties and seeds`() {
        for (d in 0..10) for (seed in 1L..20L) {
            val p = LinearAlgebraProblems.dotProduct(RandomSource.create(seed), d)
            assertEquals(LinAlgAnswerKind.NUMBER, p.answerKind)
            assertEquals(dot(p.vecA!!, p.vecB!!), p.answerNumber, "d=$d seed=$seed")
            assertEquals(p.vecA!!.size, p.vecB!!.size)
        }
    }

    @Test
    fun `solve system answer satisfies Ax = b`() {
        for (d in 0..10) for (seed in 1L..20L) {
            val p = LinearAlgebraProblems.matrixEval(RandomSource.create(seed), d)
            assertEquals(LinAlgAnswerKind.MATRIX, p.answerKind)
            // 解 x 满足 A·x = b，且答案即为 x
            assertEquals(p.rhs, p.opA!! * p.opB!!, "d=$d seed=$seed")
            assertEquals(p.opB!!.toAnswerString(), p.answerMatrix)
        }
    }

    @Test
    fun `matrix-vector answer equals product`() {
        for (d in 0..10) for (seed in 1L..20L) {
            val p = LinearAlgebraProblems.matrixVector(RandomSource.create(seed), d)
            assertEquals(LinAlgAnswerKind.MATRIX, p.answerKind)
            assertEquals(1, p.opB!!.cols) // 列向量
            assertEquals((p.opA!! * p.opB!!).toAnswerString(), p.answerMatrix, "d=$d seed=$seed")
        }
    }

    @Test
    fun `inverse operand times answer is identity`() {
        for (d in 0..10) for (seed in 1L..20L) {
            val p = LinearAlgebraProblems.inverse(RandomSource.create(seed), d)
            assertEquals(LinAlgAnswerKind.MATRIX, p.answerKind)
            val a = p.opA!!
            val inv = p.opB!!
            assertEquals(IntMatrix.identity(a.rows), a * inv, "d=$d seed=$seed : A·A⁻¹ ≠ I")
            assertEquals(inv.toAnswerString(), p.answerMatrix)
            assertTrue(a != IntMatrix.identity(a.rows), "A should not be identity")
        }
    }

    @Test
    fun `matrix size grows with difficulty`() {
        val r = RandomSource.create(42)
        // 点积维度：2 → 5
        assertEquals(2, LinearAlgebraProblems.dotProduct(r, 0).vecA!!.size)
        assertEquals(5, LinearAlgebraProblems.dotProduct(r, 9).vecA!!.size)
        // 矩阵-向量阶数：2 → 4
        assertEquals(2, LinearAlgebraProblems.matrixVector(r, 0).opA!!.rows)
        assertEquals(4, LinearAlgebraProblems.matrixVector(r, 9).opA!!.rows)
        // 逆矩阵阶数：2 → 3
        assertEquals(2, LinearAlgebraProblems.inverse(r, 0).opA!!.rows)
        assertEquals(3, LinearAlgebraProblems.inverse(r, 8).opA!!.rows)
        // 矩阵求值阶数：2 → 4
        assertEquals(2, LinearAlgebraProblems.matrixEval(r, 0).opA!!.rows)
        assertEquals(4, LinearAlgebraProblems.matrixEval(r, 9).opA!!.rows)
    }
}
