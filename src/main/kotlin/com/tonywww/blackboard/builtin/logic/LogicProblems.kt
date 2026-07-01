package com.tonywww.blackboard.builtin.logic

import net.minecraft.util.RandomSource

/** 一道生成好的逻辑值运算题：题面 LaTeX、纯文本兜底、标准真值答案。 */
data class LogicProblem(val stemLatex: String, val stemInfix: String, val answer: Boolean)

/**
 * 逻辑值运算 · 出题逻辑。两种题型：
 *  - [evaluation]：常量求值——叶子全是 T/F 字面，求整棵表达式真值。
 *  - [assignment]：变量赋值——叶子是命题变量，给定赋值后求真值。
 *
 * 难度（0–10）控制表达式规模（叶子数）与算符集：基础 AND/OR/NOT；`d>=2` 加 XOR；`d>=5` 加 →/↔。
 * 布尔恒有定义，无域安全问题。
 */
object LogicProblems {

    fun evaluation(r: RandomSource, difficulty: Int): LogicProblem {
        val d = difficulty.coerceIn(0, 10)
        val expr = randomTree(r, leafCount(d), d) { Lit(r.nextBoolean()) }
        return LogicProblem(expr.toLatex(), expr.toInfix(), expr.eval())
    }

    fun assignment(r: RandomSource, difficulty: Int): LogicProblem {
        val d = difficulty.coerceIn(0, 10)
        val nVars = (2 + d / 3).coerceIn(2, 5)
        val pool = (0 until nVars).map { 'p' + it }
        val expr = randomTree(r, leafCount(d), d) { Var(pool[r.nextInt(pool.size)]) }
        val used = expr.vars().sorted()
        val env = used.associateWith { r.nextBoolean() }
        val assignLatex = used.joinToString(",\\; ") { "$it = ${litLatex(env.getValue(it))}" }
        val assignInfix = used.joinToString(", ") { "$it=${if (env.getValue(it)) "T" else "F"}" }
        return LogicProblem(
            "$assignLatex \\;:\\; ${expr.toLatex()}",
            "$assignInfix : ${expr.toInfix()}",
            expr.eval(env),
        )
    }

    // ---- 内部 ----

    private fun litLatex(b: Boolean) = if (b) "\\text{T}" else "\\text{F}"

    /** 叶子数随难度线性增长（避免指数级），2..8。 */
    private fun leafCount(d: Int) = (2 + d).coerceIn(2, 8)

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
