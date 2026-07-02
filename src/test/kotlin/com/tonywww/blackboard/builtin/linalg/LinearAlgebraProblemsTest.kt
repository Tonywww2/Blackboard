package com.tonywww.blackboard.builtin.linalg

import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `singular values are non-negative descending integers matching AtA`() {
        for (d in 0..10) for (seed in 1L..20L) {
            val p = LinearAlgebraProblems.singularValues(RandomSource.create(seed), d)
            assertEquals(LinAlgAnswerKind.MATRIX, p.answerKind)
            val m = p.opA!!
            val sig = p.opB!!
            assertEquals(1, sig.cols, "奇异值应为列向量 d=$d seed=$seed")
            val vals = (0 until sig.rows).map { sig[it, 0] }
            assertEquals(vals.sortedDescending(), vals, "应降序 d=$d seed=$seed")
            assertTrue(vals.all { it >= 0 }, "应非负 d=$d seed=$seed")
            assertEquals(sig.toAnswerString(), p.answerMatrix)
            // σ² 为 AᵀA 的特征值：trace(AᵀA)=Σσ²；trace((AᵀA)²)=Σσ⁴=对称阵全元素平方和（对任意阶成立）
            val ata = m.transpose() * m
            val trace = (0 until ata.rows).sumOf { ata[it, it] }
            assertEquals(vals.sumOf { it * it }, trace, "trace≠Σσ² d=$d seed=$seed")
            val sumSq = (0 until ata.rows).sumOf { i -> (0 until ata.cols).sumOf { j -> ata[i, j] * ata[i, j] } }
            assertEquals(vals.sumOf { it * it * it * it }, sumSq, "Σ元素²≠Σσ⁴ d=$d seed=$seed")
        }
    }

    @Test
    fun `matrix size grows with difficulty`() {
        val r = RandomSource.create(42)
        // 点积维度：2 → 7
        assertEquals(2, LinearAlgebraProblems.dotProduct(r, 0).vecA!!.size)
        assertEquals(7, LinearAlgebraProblems.dotProduct(r, 10).vecA!!.size)
        // 矩阵-向量阶数：2 → 6
        assertEquals(2, LinearAlgebraProblems.matrixVector(r, 0).opA!!.rows)
        assertEquals(6, LinearAlgebraProblems.matrixVector(r, 10).opA!!.rows)
        // 逆矩阵阶数：2 → 4
        assertEquals(2, LinearAlgebraProblems.inverse(r, 0).opA!!.rows)
        assertEquals(4, LinearAlgebraProblems.inverse(r, 10).opA!!.rows)
        // 解 Ax=b 阶数：2 → 5
        assertEquals(2, LinearAlgebraProblems.matrixEval(r, 0).opA!!.rows)
        assertEquals(5, LinearAlgebraProblems.matrixEval(r, 10).opA!!.rows)
        // 奇异值阶数：2 → 4
        assertEquals(2, LinearAlgebraProblems.singularValues(r, 0).opA!!.rows)
        assertEquals(4, LinearAlgebraProblems.singularValues(r, 10).opA!!.rows)
    }

    @Test
    fun `inverse and solve matrices are non-trivial at higher difficulty`() {
        for (seed in 1L..30L) {
            assertFalse(LinearAlgebraProblems.inverse(RandomSource.create(seed), 8).opA!!.isTriangular(), "inverse A 三角 seed=$seed")
            assertFalse(LinearAlgebraProblems.matrixEval(RandomSource.create(seed), 8).opA!!.isTriangular(), "solve A 三角 seed=$seed")
        }
    }
}
