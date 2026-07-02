package com.tonywww.blackboard.builtin.logic

import net.minecraft.util.RandomSource

/** 一道生成好的逻辑值运算题：题面 LaTeX、纯文本兜底、标准真值答案。 */
data class LogicProblem(val stemLatex: String, val stemInfix: String, val answer: Boolean)

/**
 * 一道生成好的化简题：题面（待化简的式子）+ 判题所需的等价基准（原式 infix，供解析比真值表）
 * 与目标复杂度（标准最简式的结点数，供判「够简」）。
 */
data class SimplifyProblem(
    val stemLatex: String,
    val stemInfix: String,
    val baseInfix: String,
    val targetInfix: String,
    val targetSize: Int,
)

/**
 * 逻辑值运算 · 出题逻辑。两种题型：
 *  - [evaluation]：常量求值——叶子全是 T/F 字面，求整棵表达式真值。
 *  - [simplify]：布尔化简——把一个（人为复杂化的）含变量表达式化简为最简等价形式。
 *
 * 难度（0–10）控制表达式规模与算符集：基础 AND/OR/NOT；`d>=2` 加 XOR；`d>=5` 加 →/↔（求值题）。
 * 布尔恒有定义，无域安全问题。
 */
object LogicProblems {

    fun evaluation(r: RandomSource, difficulty: Int): LogicProblem {
        val d = difficulty.coerceIn(0, 10)
        val expr = randomTree(r, leafCount(d), d) { Lit(r.nextBoolean()) }
        return LogicProblem(expr.toLatex(), expr.toInfix(), expr.eval())
    }

    /**
     * 布尔化简：先随机取一个「简单目标」[simplifyTarget]（常量/单变量/简单二元式），再用若干保持等价的
     * 恒等变换 [complicate] 人为复杂化成 `expr`（`expr ≡ target`），让玩家把 `expr` 化简回最简等价形式。
     * 判题（见 `LogicGenerators`）：解析玩家输入 → 与 `expr` 真值表等价、且复杂度不超过 `target` 的结点数。
     */
    fun simplify(r: RandomSource, difficulty: Int): SimplifyProblem {
        val d = difficulty.coerceIn(0, 10)
        val target = simplifyTarget(r, d)
        val steps = (2 + d / 2).coerceIn(2, 7)
        var expr: BoolExpr = target
        repeat(steps) { expr = complicate(expr, r, d) }
        return SimplifyProblem(
            stemLatex = "\\text{Simplify:}\\; " + expr.toLatex(),
            stemInfix = "simplify: " + expr.toInfix(),
            baseInfix = expr.toInfix(),
            targetInfix = target.toInfix(),
            targetSize = target.size(),
        )
    }

    // ---- 内部 ----

    /** 叶子数随难度线性增长（避免指数级），2..8。 */
    private fun leafCount(d: Int) = (2 + d).coerceIn(2, 8)

    /** 化简题变量数（题面变量池 p,q,r,s 的前若干个），随难度 2..4。 */
    private fun varCount(d: Int) = (2 + d / 4).coerceIn(2, 4)

    /** 随机「简单目标」最简式：低难度常量/单变量，高难度含简单二元式。 */
    private fun simplifyTarget(r: RandomSource, d: Int): BoolExpr {
        val vars = (0 until varCount(d)).map { Var('p' + it) }
        return when {
            d <= 2 -> when (r.nextInt(3)) {
                0 -> Lit(true)
                1 -> Lit(false)
                else -> maybeNot(r, vars[r.nextInt(vars.size)])
            }
            d <= 6 -> if (r.nextBoolean()) {
                maybeNot(r, vars[r.nextInt(vars.size)])
            } else {
                Bin(if (r.nextBoolean()) BinOp.AND else BinOp.OR, vars[0], vars[1])
            }
            else -> Bin(
                listOf(BinOp.AND, BinOp.OR, BinOp.XOR)[r.nextInt(3)],
                maybeNot(r, vars[0]),
                maybeNot(r, vars[1]),
            )
        }
    }

    private fun maybeNot(r: RandomSource, e: BoolExpr): BoolExpr = if (r.nextInt(3) == 0) Not(e) else e

    /**
     * 对 `e` 施加一次保持等价的恒等变换，使结果更复杂但 `≡ e`：
     * 双重否定 `¬¬e`；补零 `e∨(x∧¬x)`；补一 `e∧(x∨¬x)`；`e` 较小时可用吸收 `e∧(e∨x)`/`e∨(e∧x)`。
     * 只在 `e` 小时复制 `e`，避免规模指数膨胀；引入的变量 `x` 取自同一变量池（真值表规模受控）。
     */
    private fun complicate(e: BoolExpr, r: RandomSource, d: Int): BoolExpr {
        val x = Var('p' + r.nextInt(varCount(d)))
        val choice = if (e.size() <= 4) r.nextInt(6) else r.nextInt(4)
        return when (choice) {
            0 -> Not(Not(e))
            1 -> Bin(BinOp.OR, e, Bin(BinOp.AND, x, Not(x)))
            2 -> Bin(BinOp.AND, e, Bin(BinOp.OR, x, Not(x)))
            3 -> Bin(BinOp.OR, Bin(BinOp.AND, x, Not(x)), e)
            4 -> Bin(BinOp.AND, e, Bin(BinOp.OR, e, x))
            else -> Bin(BinOp.OR, e, Bin(BinOp.AND, e, x))
        }
    }

    /**
     * 构造含 [leaves] 个叶子的随机布尔表达式树（规模线性可控），并随机点缀否定。
     * 每个内部结点用难度可用的二元算符；约 1/4 概率对子树/叶子取否定。
     */
    private fun randomTree(r: RandomSource, leaves: Int, difficulty: Int, leaf: () -> BoolExpr): BoolExpr {
        val base: BoolExpr = if (leaves <= 1) {
            leaf()
        } else {
            val left = 1 + r.nextInt(leaves - 1)
            val ops = opsFor(difficulty)
            Bin(
                ops[r.nextInt(ops.size)],
                randomTree(r, left, difficulty, leaf),
                randomTree(r, leaves - left, difficulty, leaf),
            )
        }
        return if (r.nextInt(4) == 0) Not(base) else base
    }

    private fun opsFor(d: Int): List<BinOp> = buildList {
        add(BinOp.AND)
        add(BinOp.OR)
        if (d >= 2) add(BinOp.XOR)
        if (d >= 5) {
            add(BinOp.IMP)
            add(BinOp.IFF)
        }
    }
}
