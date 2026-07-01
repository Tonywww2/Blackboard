package com.tonywww.blackboard.builtin.linalg

import net.minecraft.util.RandomSource

/** 题型：点积 / 矩阵求值（解线性方程组 Ax=b）/ 矩阵-向量积 / 逆矩阵。 */
enum class LinAlgType { DOT, EVAL, MATVEC, INVERSE }

/** 标准答案形态：一个数 / 一个矩阵（`[[..]]` 串）。 */
enum class LinAlgAnswerKind { NUMBER, MATRIX }

/**
 * 一道生成好的线性代数题。
 *
 * `answerKind=NUMBER` 用 [answerNumber]（判题走 [com.tonywww.blackboard.validation.Validators.number]）；
 * `answerKind=MATRIX` 用 [answerMatrix]（`[[..]]` 串，判题走 [com.tonywww.blackboard.validation.Validators.matrix]）。
 * [opA]/[opB] 为内存内测试/自洽校验的辅助字段，不进 NBT。
 */
data class LinAlgProblem(
    val type: LinAlgType,
    val stemLatex: String,
    val stemInfix: String,
    val answerKind: LinAlgAnswerKind,
    val answerNumber: Int? = null,
    val answerMatrix: String? = null,
    internal val opA: IntMatrix? = null,          // 主操作矩阵（求值/矩阵-向量积/逆 的 A）
    internal val opB: IntMatrix? = null,          // 求值的解 x / 矩阵-向量积的列向量 / 逆题的 A⁻¹
    internal val vecA: List<Int>? = null,         // 点积左向量
    internal val vecB: List<Int>? = null,         // 点积右向量
    internal val rhs: IntMatrix? = null,          // 求值 Ax=b 的右端项 b
)

/**
 * 线性代数模块 · 出题逻辑（纯逻辑，仅依赖 [RandomSource] 与 [IntMatrix]）。
 *
 * 规模随 `difficulty`(0–10) 增大：向量维度 / 矩阵阶数逐级变大，元素幅度也放宽（见 [sizeFor]）。
 */
object LinearAlgebraProblems {

    /** 规模：难度每升 3 级 +1，钳制到 `[min,max]`（难度 0→min，9→min+3）。 */
    private fun sizeFor(difficulty: Int, min: Int, max: Int): Int =
        (min + difficulty.coerceAtLeast(0) / 3).coerceIn(min, max)

    /** 随机整数，幅度 `[-mag,mag]`（可为 0）。 */
    private fun rnd(r: RandomSource, mag: Int): Int =
        r.nextIntBetweenInclusive(-mag.coerceAtLeast(1), mag.coerceAtLeast(1))

    private fun randomVector(r: RandomSource, n: Int, mag: Int): List<Int> =
        (0 until n).map { rnd(r, mag) }

    private fun randomMatrix(r: RandomSource, rows: Int, cols: Int, mag: Int): IntMatrix =
        IntMatrix((0 until rows).map { (0 until cols).map { rnd(r, mag) } })

    /** 点积：两个 n 维向量（n 随难度 2→5，元素幅度 3+d），答案为整数。 */
    fun dotProduct(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = sizeFor(d, 2, 5)
        val mag = 3 + d
        val a = randomVector(r, n, mag)
        val b = randomVector(r, n, mag)
        val av = IntMatrix(a.map { listOf(it) }) // 列向量 LaTeX
        val bv = IntMatrix(b.map { listOf(it) })
        return LinAlgProblem(
            type = LinAlgType.DOT,
            stemLatex = av.toLatex() + " \\cdot " + bv.toLatex(),
            stemInfix = a.toString() + " · " + b.toString() + " = ?",
            answerKind = LinAlgAnswerKind.NUMBER,
            answerNumber = dot(a, b),
            vecA = a, vecB = b,
        )
    }

    /**
     * 矩阵求值：求解线性方程组 `Ax=b` 得到 x。构造可逆整数阵 A 与整数解 x、令 `b=A·x`，展示 A 与 b、
     * 答案为列向量 x（`[[x1],[x2]]`）。A 取幺模阵保证可逆、解唯一；阶数随难度 2→4。
     */
    fun matrixEval(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = sizeFor(d, 2, 4)
        val a = unimodularPair(r, n, shears = 2 + d / 3).first // 可逆整数阵 → 解唯一
        val magX = 2 + d / 2
        val x = IntMatrix((0 until n).map { listOf(rnd(r, magX)) }) // 整数解 x (n×1)
        val b = a * x
        return LinAlgProblem(
            type = LinAlgType.EVAL,
            stemLatex = a.toLatex() + " \\vec{x} = " + b.toLatex(),
            stemInfix = a.toAnswerString() + " x = " + b.toAnswerString() + " , x = ?",
            answerKind = LinAlgAnswerKind.MATRIX,
            answerMatrix = x.toAnswerString(),
            opA = a, opB = x, rhs = b,
        )
    }

    /** 矩阵-向量积：n 阶方阵 × n 维列向量（n 随难度 2→4），答案为 n×1 列向量。 */
    fun matrixVector(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = sizeFor(d, 2, 4)
        val mag = 2 + d / 2
        val a = randomMatrix(r, n, n, mag)
        val v = IntMatrix((0 until n).map { listOf(rnd(r, mag)) }) // n×1 列向量
        val prod = a * v
        return LinAlgProblem(
            type = LinAlgType.MATVEC,
            stemLatex = a.toLatex() + " " + v.toLatex(),
            stemInfix = a.toAnswerString() + " * " + v.toAnswerString() + " = ?",
            answerKind = LinAlgAnswerKind.MATRIX,
            answerMatrix = prod.toAnswerString(),
            opA = a, opB = v,
        )
    }

    /** 逆矩阵：构造幺模整数阵（保证整数逆），阶数随难度 2→3，答案为 A⁻¹。 */
    fun inverse(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = if (d < 5) 2 else 3
        val (a, inv) = unimodularPair(r, n, shears = 2 + d / 3)
        return LinAlgProblem(
            type = LinAlgType.INVERSE,
            stemLatex = a.toLatex() + "^{-1}",
            stemInfix = a.toAnswerString() + "^(-1) = ?",
            answerKind = LinAlgAnswerKind.MATRIX,
            answerMatrix = inv.toAnswerString(),
            opA = a, opB = inv,
        )
    }

    /**
     * 构造 n 阶幺模整数矩阵对 `(A, A⁻¹)`：`A = ∏ Eₜ`（随机初等错切阵之积），`A⁻¹ = ∏ Eₜ⁻¹`（逆序）。
     * 两者恒为整数矩阵；元素幅度过大或退化为单位阵则重试（上限内），以保证题目可读、答案可输入。
     */
    private fun unimodularPair(r: RandomSource, n: Int, shears: Int): Pair<IntMatrix, IntMatrix> {
        val idN = IntMatrix.identity(n)
        repeat(32) {
            var a = idN
            var inv = idN
            repeat(shears.coerceAtLeast(2)) {
                val i = r.nextInt(n)
                var j = r.nextInt(n)
                while (i == j) j = r.nextInt(n)
                val m = (if (r.nextBoolean()) 1 else -1) * r.nextIntBetweenInclusive(1, 2)
                a *= IntMatrix.shear(n, i, j, m)
                inv = IntMatrix.shear(n, i, j, -m) * inv
            }
            if (a != idN && a.maxAbs() <= 20 && inv.maxAbs() <= 20) return a to inv
        }
        // 兜底：单次错切（元素必然小、非恒等、整数逆）。
        val j = if (n > 1) 1 else 0
        return IntMatrix.shear(n, 0, j, 1) to IntMatrix.shear(n, 0, j, -1)
    }
}
