package com.tonywww.blackboard.builtin.logic

/**
 * 命题逻辑表达式 AST（纯 Kotlin，可单测）。
 *
 * 叶子：[Lit]（T/F 字面）、[Var]（命题变量）。一元 [Not]，二元 [Bin]（算符见 [BinOp]）。
 * [eval] 在给定变量赋值 [env] 下求真值；[toLatex] 供 JLaTeXMath 渲染；[toInfix] 纯文本兜底；
 * [vars] 收集用到的变量。二元子式在更外层（二元/否定）下自动加括号以消除歧义（保留树结构）。
 */
sealed interface BoolExpr {
    fun eval(env: Map<Char, Boolean> = emptyMap()): Boolean
    fun toLatex(): String
    fun toInfix(): String
    fun vars(): Set<Char>
}

/** 二元逻辑算符：真值表 + LaTeX/文本符号。 */
enum class BinOp(val latex: String, val infix: String) {
    AND("\\land", "AND"),
    OR("\\lor", "OR"),
    XOR("\\oplus", "XOR"),
    IMP("\\rightarrow", "->"),
    IFF("\\leftrightarrow", "<->"),
    ;

    fun apply(x: Boolean, y: Boolean): Boolean = when (this) {
        AND -> x && y
        OR -> x || y
        XOR -> x != y
        IMP -> !x || y
        IFF -> x == y
    }
}

data class Lit(val value: Boolean) : BoolExpr {
    override fun eval(env: Map<Char, Boolean>) = value
    override fun toLatex() = if (value) "\\text{T}" else "\\text{F}"
    override fun toInfix() = if (value) "T" else "F"
    override fun vars() = emptySet<Char>()
}

data class Var(val name: Char) : BoolExpr {
    override fun eval(env: Map<Char, Boolean>) = env[name] ?: error("未赋值的逻辑变量 $name")
    override fun toLatex() = name.toString()
    override fun toInfix() = name.toString()
    override fun vars() = setOf(name)
}

data class Not(val e: BoolExpr) : BoolExpr {
    override fun eval(env: Map<Char, Boolean>) = !e.eval(env)
    override fun toLatex() = "\\lnot " + parenLatex(e)
    override fun toInfix() = "NOT " + parenInfix(e)
    override fun vars() = e.vars()
}

data class Bin(val op: BinOp, val a: BoolExpr, val b: BoolExpr) : BoolExpr {
    override fun eval(env: Map<Char, Boolean>) = op.apply(a.eval(env), b.eval(env))
    override fun toLatex() = parenLatex(a) + " " + op.latex + " " + parenLatex(b)
    override fun toInfix() = parenInfix(a) + " " + op.infix + " " + parenInfix(b)
    override fun vars() = a.vars() + b.vars()
}

/** 二元子式在更外层运算下加括号（保留树结构）；否定/字面/变量无需括号（否定绑定最紧）。 */
private fun parenLatex(e: BoolExpr): String = if (e is Bin) "\\left(" + e.toLatex() + "\\right)" else e.toLatex()

private fun parenInfix(e: BoolExpr): String = if (e is Bin) "(" + e.toInfix() + ")" else e.toInfix()
