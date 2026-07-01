package com.tonywww.blackboard.builtin.linalg

import kotlin.math.abs

/**
 * 不可变整数矩阵（行优先 [values]），线性代数题库的纯逻辑核心：矩阵/向量运算 + LaTeX 生成 +
 * 答案串序列化（`[[a,b],[c,d]]`，与 [com.tonywww.blackboard.validation.Validators.matrix] 对齐）。
 *
 * 不引用任何 MC 平台类型，便于纯单元测试。列向量表示为 n×1 矩阵（答案串写作 `[[x],[y]]`）。
 */
data class IntMatrix(val values: List<List<Int>>) {
    val rows: Int get() = values.size
    val cols: Int get() = if (values.isEmpty()) 0 else values[0].size

    init {
        require(values.isNotEmpty()) { "matrix must have at least one row" }
        require(values.all { it.size == values[0].size }) { "all rows must have equal length" }
        require(values[0].isNotEmpty()) { "matrix must have at least one column" }
    }

    operator fun get(r: Int, c: Int): Int = values[r][c]

    /** 转置（m×n → n×m）。 */
    fun transpose(): IntMatrix =
        IntMatrix((0 until cols).map { c -> (0 until rows).map { r -> values[r][c] } })

    /** 矩阵乘法（`this` 为 m×k，[other] 为 k×n → m×n）。 */
    operator fun times(other: IntMatrix): IntMatrix {
        require(cols == other.rows) { "shape mismatch: ${rows}x$cols * ${other.rows}x${other.cols}" }
        return IntMatrix(
            (0 until rows).map { r ->
                (0 until other.cols).map { c ->
                    var s = 0
                    for (k in 0 until cols) s += this[r, k] * other[k, c]
                    s
                }
            },
        )
    }

    /** 全部元素绝对值的最大值（用于约束题目规模、避免答案过大难以输入）。 */
    fun maxAbs(): Int = values.maxOf { row -> row.maxOf { abs(it) } }

    /** 显示用 LaTeX：`\begin{pmatrix} a & b \\ c & d \end{pmatrix}`（JLaTeXMath 可渲染）。 */
    fun toLatex(): String = buildString {
        append("\\begin{pmatrix}")
        for (r in 0 until rows) {
            append(values[r].joinToString(" & "))
            if (r < rows - 1) append(" \\\\ ")
        }
        append("\\end{pmatrix}")
    }

    /** 答案串 `[[a,b],[c,d]]`（无空白，与 [com.tonywww.blackboard.validation.Validators.parseMatrix] 一致）。 */
    fun toAnswerString(): String =
        values.joinToString(",", "[", "]") { row -> row.joinToString(",", "[", "]") { it.toString() } }

    companion object {
        /** n 阶单位阵。 */
        fun identity(n: Int): IntMatrix =
            IntMatrix((0 until n).map { r -> (0 until n).map { c -> if (r == c) 1 else 0 } })

        /**
         * n 阶初等错切阵 `I + m·E_{ij}`（要求 i≠j）：左乘时执行「第 i 行 += m·第 j 行」，
         * 其逆为 `I - m·E_{ij}`。行列式恒为 1，故错切阵之积为幺模整数阵、逆亦为整数阵。
         */
        fun shear(n: Int, i: Int, j: Int, m: Int): IntMatrix {
            require(i != j) { "shear requires i != j" }
            return IntMatrix(
                (0 until n).map { r ->
                    (0 until n).map { c ->
                        (if (r == c) 1 else 0) + (if (r == i && c == j) m else 0)
                    }
                },
            )
        }
    }
}

/** 整数向量点积（两向量长度须一致）。 */
fun dot(a: List<Int>, b: List<Int>): Int {
    require(a.size == b.size) { "dot product requires equal-length vectors" }
    var s = 0
    for (k in a.indices) s += a[k] * b[k]
    return s
}
