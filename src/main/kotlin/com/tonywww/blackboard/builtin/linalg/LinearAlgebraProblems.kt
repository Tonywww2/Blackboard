package com.tonywww.blackboard.builtin.linalg

import net.minecraft.util.RandomSource

/** 题型：点积 / 矩阵求值（解线性方程组 Ax=b）/ 矩阵-向量积 / 逆矩阵 / 奇异值。 */
enum class LinAlgType { DOT, EVAL, MATVEC, INVERSE, SVD }

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

    /** 规模：难度每升 2 级 +1，钳制到 `[min,max]`（难度 0→min，10→min+5）。 */
    private fun sizeFor(difficulty: Int, min: Int, max: Int): Int =
        (min + difficulty.coerceAtLeast(0) / 2).coerceIn(min, max)

    /** 随机整数，幅度 `[-mag,mag]`（可为 0）。 */
    private fun rnd(r: RandomSource, mag: Int): Int =
        r.nextIntBetweenInclusive(-mag.coerceAtLeast(1), mag.coerceAtLeast(1))

    private fun randomVector(r: RandomSource, n: Int, mag: Int): List<Int> =
        (0 until n).map { rnd(r, mag) }

    private fun randomMatrix(r: RandomSource, rows: Int, cols: Int, mag: Int): IntMatrix =
        IntMatrix((0 until rows).map { (0 until cols).map { rnd(r, mag) } })

    /** 点积：两个 n 维向量（n 随难度 2→7，元素幅度 3+2d），答案为整数。 */
    fun dotProduct(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = sizeFor(d, 2, 7)
        val mag = 3 + 2 * d
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
     * 答案为列向量 x（`[[x1],[x2]]`）。A 由错切阵之积构造（可逆、解唯一）；阶数随难度 2→5。
     */
    fun matrixEval(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = (2 + d / 3).coerceIn(2, 5)
        val a = invertibleMatrix(r, n, shears = 3 + d / 2, cap = 14 + d) // 可逆整数阵 → 解唯一
        val magX = 2 + d / 3
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

    /** 矩阵-向量积：n 阶方阵 × n 维列向量（n 随难度 2→6，元素幅度 2+d），答案为 n×1 列向量。 */
    fun matrixVector(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = sizeFor(d, 2, 6)
        val mag = 2 + d
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

    /** 逆矩阵：构造幺模整数阵（保证整数逆），阶数随难度 2→4、错切深度与元素幅度随难度增大，答案为 A⁻¹。 */
    fun inverse(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = (2 + d / 4).coerceIn(2, 4)
        val (a, inv) = unimodularPair(r, n, shears = 4 + d / 2, cap = 16 + 8 * d)
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
     * 奇异值：构造**列两两正交**、各列范数为整数的整数矩阵，使 `AᵀA` 为对角阵、奇异值均为整数。
     * **阶数随难度 2→4**（n=2 用毕达哥拉斯正交列；n=3/4 用已知整数正交基，列范数分别为 3、2）。
     * 每列再乘不同整数标量 cᵢ → 奇异值 = 列范数·cᵢ；随机列符号翻转/行置换/转置不改奇异值，只增视觉多样。
     * 答案为**降序**奇异值列向量（`[[σ1],…]`）。
     */
    fun singularValues(r: RandomSource, difficulty: Int): LinAlgProblem {
        val d = difficulty.coerceIn(0, 10)
        val n = (2 + d / 4).coerceIn(2, 4)
        val (base, baseNorm) = orthogonalColumnBase(r, n, d)
        val maxScale = (1 + d / 3).coerceIn(1, 4)
        val scales = IntArray(n) { 1 + r.nextInt(maxScale) }
        // A 的第 c 列 = scales[c] · base 第 c 列（列仍两两正交，范数 = baseNorm·scales[c]）
        var m = IntMatrix((0 until n).map { row -> (0 until n).map { c -> base[row, c] * scales[c] } })
        if (r.nextBoolean()) m = m.transpose() // 奇异值对转置不变
        val sigmas = scales.map { baseNorm * it }.sortedDescending()
        val answer = IntMatrix(sigmas.map { listOf(it) })
        return LinAlgProblem(
            type = LinAlgType.SVD,
            stemLatex = "\\sigma\\!\\left(" + m.toLatex() + "\\right) = ?",
            stemInfix = "sigma(" + m.toAnswerString() + ") = ? (降序 [[s1],...])",
            answerKind = LinAlgAnswerKind.MATRIX,
            answerMatrix = answer.toAnswerString(),
            opA = m, opB = answer,
        )
    }

    /**
     * n 阶「列两两正交、每列范数相等且为整数」的整数基阵 + 列范数：n=2 取毕达哥拉斯 `[[p,q],[q,-p]]`（范数 s）；
     * n=3 取范数 3 的整数正交基；n=4 取 Hadamard（范数 2）。再随机列符号翻转 + 行置换（保持列正交与范数）增多样性。
     */
    private fun orthogonalColumnBase(r: RandomSource, n: Int, difficulty: Int): Pair<IntMatrix, Int> {
        val (base, norm) = when (n) {
            2 -> {
                val (p, q, s) = pythagorean(r, difficulty)
                listOf(listOf(p, q), listOf(q, -p)) to s
            }
            3 -> listOf(listOf(2, 2, -1), listOf(2, -1, 2), listOf(-1, 2, 2)) to 3
            else -> listOf(
                listOf(1, 1, 1, 1), listOf(1, -1, 1, -1),
                listOf(1, 1, -1, -1), listOf(1, -1, -1, 1),
            ) to 2
        }
        val colSigns = IntArray(n) { if (r.nextBoolean()) 1 else -1 }
        val perm = (0 until n).toMutableList()
        for (i in n - 1 downTo 1) {
            val k = r.nextInt(i + 1)
            val t = perm[i]; perm[i] = perm[k]; perm[k] = t
        }
        val m = IntMatrix(perm.map { row -> (0 until n).map { c -> base[row][c] * colSigns[c] } })
        return m to norm
    }

    /** 毕达哥拉斯三元组 `(p,q,s)`（`p²+q²=s²`）：按难度选规模（低难度含轴对齐 `(1,0,1)`，答案退化为对角元）。 */
    private fun pythagorean(r: RandomSource, difficulty: Int): Triple<Int, Int, Int> {
        val easy = listOf(Triple(1, 0, 1), Triple(0, 1, 1), Triple(3, 4, 5), Triple(4, 3, 5))
        val mid = listOf(Triple(6, 8, 10), Triple(8, 6, 10), Triple(5, 12, 13), Triple(12, 5, 13))
        val hard = listOf(Triple(8, 15, 17), Triple(15, 8, 17), Triple(9, 12, 15), Triple(12, 9, 15))
        val pool = when {
            difficulty <= 2 -> easy
            difficulty <= 6 -> easy + mid
            else -> easy + mid + hard
        }
        return pool[r.nextInt(pool.size)]
    }

    /**
     * 构造 n 阶幺模整数矩阵对 `(A, A⁻¹)`：`A = ∏ Eₜ`（随机初等错切阵之积），`A⁻¹ = ∏ Eₜ⁻¹`（逆序）。
     * 两者恒为整数矩阵。为避免“近单位/三角阵”这类过于好算的结果，用 richness 打分（对角线上 1 的个数 +
     * 三角阵重罚）择优：优先取对角线无 1 且非三角的稠密矩阵；元素幅度超过 [cap] 或退化为单位阵者跳过。
     */
    private fun unimodularPair(r: RandomSource, n: Int, shears: Int, cap: Int): Pair<IntMatrix, IntMatrix> {
        val idN = IntMatrix.identity(n)
        var best: Pair<IntMatrix, IntMatrix>? = null
        var bestScore = Int.MAX_VALUE
        repeat(96) {
            var a = idN
            var inv = idN
            repeat(shears.coerceAtLeast(2)) {
                val i = r.nextInt(n)
                var j = r.nextInt(n)
                while (i == j) j = r.nextInt(n)
                val m = (if (r.nextBoolean()) 1 else -1) * r.nextIntBetweenInclusive(1, 3)
                a *= IntMatrix.shear(n, i, j, m)
                inv = IntMatrix.shear(n, i, j, -m) * inv
            }
            if (a == idN || a.maxAbs() > cap || inv.maxAbs() > cap) return@repeat
            val score = a.diagonalOnes() + if (a.isTriangular()) n else 0
            if (score < bestScore) { bestScore = score; best = a to inv }
            if (score == 0) return a to inv
        }
        return best ?: run {
            val j = if (n > 1) 1 else 0
            IntMatrix.shear(n, 0, j, 1) to IntMatrix.shear(n, 0, j, -1)
        }
    }

    /**
     * 构造 n 阶可逆整数矩阵（错切阵之积）：仅约束自身元素幅度 ≤[cap]（不关心其逆的规模，用于「解 Ax=b」）。
     * 同样用 richness 打分择优（对角线 1 越少越好、三角阵重罚），避免近单位/三角这类可直接回代的简单矩阵。
     */
    private fun invertibleMatrix(r: RandomSource, n: Int, shears: Int, cap: Int): IntMatrix {
        val idN = IntMatrix.identity(n)
        var best: IntMatrix? = null
        var bestScore = Int.MAX_VALUE
        repeat(96) {
            var a = idN
            repeat(shears.coerceAtLeast(2)) {
                val i = r.nextInt(n)
                var j = r.nextInt(n)
                while (i == j) j = r.nextInt(n)
                val m = (if (r.nextBoolean()) 1 else -1) * r.nextIntBetweenInclusive(1, 2)
                a *= IntMatrix.shear(n, i, j, m)
            }
            if (a == idN || a.maxAbs() > cap) return@repeat
            val score = a.diagonalOnes() + if (a.isTriangular()) n else 0
            if (score < bestScore) { bestScore = score; best = a }
            if (score == 0) return a
        }
        return best ?: IntMatrix.shear(n, 0, if (n > 1) 1 else 0, 1)
    }
}
