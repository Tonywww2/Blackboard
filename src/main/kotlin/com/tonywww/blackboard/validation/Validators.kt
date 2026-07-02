package com.tonywww.blackboard.validation

import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.Question

/**
 * 内置便捷校验器，返回 `(Question, AnswerContext) -> AnswerResult`，可直接用于 `QuestionGenerator.validate`。
 *
 * 约定：标准答案默认存于 `Question.data` 的 `"answer"` 键（类型随题型：String / Double / 矩阵字符串）。
 * `incorrect`（解析成功但答错，消耗机会）与 `invalid`（解析失败，不消耗机会）的区分见 answer-format §10。
 *
 * 模组核心**不内置**数值/符号求值库；[expression] 为接口先行，[ExpressionEvaluator] 的具体实现待选型。
 */
object Validators {

    /** 文字题：默认 `trim` + 大小写不敏感比较。 */
    fun text(
        expectedKey: String = "answer",
        ignoreCase: Boolean = true,
        trim: Boolean = true,
    ): (Question, AnswerContext) -> AnswerResult = { q, a ->
        var exp = q.getString(expectedKey)
        var got = a.text
        if (trim) {
            exp = exp.trim()
            got = got.trim()
        }
        val eq = if (ignoreCase) exp.equals(got, ignoreCase = true) else exp == got
        if (eq) AnswerResult.correct() else AnswerResult.incorrect()
    }

    /** 文字题：正则匹配（对 `trim` 后的答案）。 */
    fun textRegex(pattern: Regex): (Question, AnswerContext) -> AnswerResult = { _, a ->
        if (pattern.matches(a.text.trim())) AnswerResult.correct() else AnswerResult.incorrect()
    }

    /** 逻辑值题：宽松解析 true/false（含 `T/F`、`1/0`、`yes/no`、`对/错` 等）后比较；无法识别→Invalid。 */
    fun boolean(expectedKey: String = "answer"): (Question, AnswerContext) -> AnswerResult = { q, a ->
        val exp = parseBoolean(q.getString(expectedKey))
        val got = parseBoolean(a.text)
        when {
            got == null -> AnswerResult.invalid()
            exp == got -> AnswerResult.correct()
            else -> AnswerResult.incorrect()
        }
    }

    /** 数值题：字面数值解析 + 容差比较。 */
    fun number(
        expectedKey: String = "answer",
        tolerance: Double = 1e-9,
    ): (Question, AnswerContext) -> AnswerResult = { q, a ->
        val exp = q.getDouble(expectedKey)
        val got = parseNumber(a.text)
        when {
            got == null -> AnswerResult.invalid() // 无法解析为数 → 不消耗机会
            kotlin.math.abs(got - exp) <= tolerance -> AnswerResult.correct()
            else -> AnswerResult.incorrect()
        }
    }

    /** 矩阵题：解析 `[[a,b],[c,d]]` 或 LaTeX（`\begin{pmatrix}…\end{pmatrix}` 等）后逐元素按容差比较。 */
    fun matrix(
        expectedKey: String = "answer",
        tolerance: Double = 1e-9,
    ): (Question, AnswerContext) -> AnswerResult = { q, a ->
        val exp = parseMatrix(q.getString(expectedKey))
        val got = parseAnswerMatrix(a.text)
        when {
            got == null || exp == null -> if (got == null) AnswerResult.invalid() else AnswerResult.incorrect()
            got.size != exp.size || got.indices.any { got[it].size != exp[it].size } -> AnswerResult.incorrect()
            matricesEqual(exp, got, tolerance) -> AnswerResult.correct()
            else -> AnswerResult.incorrect()
        }
    }

    /**
     * 表达式/符号题（接口先行）：
     *  - 先对 `acceptedKey`（多个标准答案以 `\n` 分隔）做归一化字面比较；
     *  - 若提供 [evaluator] 与抽样点，则在若干点上比较数值是否一致（容差内）。
     */
    fun expression(
        acceptedKey: String = "answer",
        variable: String = "x",
        samples: List<Double> = emptyList(),
        tolerance: Double = 1e-6,
        evaluator: ExpressionEvaluator? = null,
    ): (Question, AnswerContext) -> AnswerResult = { q, a ->
        val accepted = q.getString(acceptedKey).split('\n').map { normalizeExpr(it) }
        val got = normalizeExpr(a.text)
        when {
            got in accepted -> AnswerResult.correct()
            evaluator != null && samples.isNotEmpty() ->
                if (numericallyEqual(accepted, a.text, variable, samples, tolerance, evaluator)) {
                    AnswerResult.correct()
                } else {
                    AnswerResult.incorrect()
                }
            else -> AnswerResult.incorrect()
        }
    }

    // --- 解析与比较工具 ---

    /** 仅做「字面数值」解析（整数/小数/科学计数/正负号 + 简单分数 `a/b`）。 */
    fun parseNumber(s: String): Double? {
        val t = s.trim()
        t.toDoubleOrNull()?.let { return it }
        val m = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*/\\s*([+-]?\\d+(?:\\.\\d+)?)$").find(t) ?: return null
        val d = m.groupValues[2].toDouble()
        return if (d == 0.0) null else m.groupValues[1].toDouble() / d
    }

    /** 宽松布尔解析：`true/t/1/yes/y/⊤/对/真/是`→true；`false/f/0/no/n/⊥/错/假/否`→false；否则 null。 */
    fun parseBoolean(s: String): Boolean? = when (s.trim().lowercase()) {
        "true", "t", "1", "yes", "y", "\u22a4", "对", "真", "是" -> true
        "false", "f", "0", "no", "n", "\u22a5", "错", "假", "否" -> false
        else -> null
    }

    /** 解析嵌套方括号矩阵 `[[a,b],[c,d]]`；空白忽略；非法返回 null。 */
    fun parseMatrix(s: String): List<List<Double>>? {
        val t = s.replace("\\s".toRegex(), "")
        if (!t.startsWith("[[") || !t.endsWith("]]")) return null
        val rowsRaw = Regex("\\[([^\\[\\]]*)\\]").findAll(t.substring(1, t.length - 1)).map { it.groupValues[1] }.toList()
        if (rowsRaw.isEmpty()) return null
        val rows = rowsRaw.map { row ->
            if (row.isEmpty()) return null
            row.split(",").map { parseNumber(it) ?: return null }
        }
        return rows
    }

    /** 解析玩家矩阵答案：先按 `[[a,b],[c,d]]`，失败再按 LaTeX 矩阵。两种写法皆可作答。 */
    fun parseAnswerMatrix(s: String): List<List<Double>>? = parseMatrix(s) ?: parseLatexMatrix(s)

    /**
     * 解析 LaTeX 矩阵 `\begin{Xmatrix} a & b \\ c & d \end{Xmatrix}`（X∈p/b/B/v/V/空）：
     * 行以 `\\` 分隔、列以 `&` 分隔；单元支持整数/小数/`\frac{a}{b}`、可选花括号包裹与 `\,` 等间距。非法返回 null。
     */
    fun parseLatexMatrix(s: String): List<List<Double>>? {
        val body = Regex("""\\begin\{[pbBvV]?matrix\}(.*?)\\end\{[pbBvV]?matrix\}""", RegexOption.DOT_MATCHES_ALL)
            .find(s.trim())?.groupValues?.get(1) ?: return null
        val rows = body.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        val cells = rows.map { row -> row.split("&").map { parseLatexNumber(it) ?: return null } }
        if (cells.any { it.size != cells[0].size }) return null
        return cells
    }

    /** LaTeX 单元数值：去空白/`\,;!`，`\frac{a}{b}`→`a/b`，去花括号，回落 [parseNumber]。 */
    private fun parseLatexNumber(cell: String): Double? {
        var t = cell.replace("\\s".toRegex(), "").replace("\\,", "").replace("\\;", "").replace("\\!", "")
        t = Regex("""\\d?frac\{([^{}]*)\}\{([^{}]*)\}""").replace(t) { "${it.groupValues[1]}/${it.groupValues[2]}" }
        t = t.removePrefix("{").removeSuffix("}")
        return parseNumber(t)
    }

    fun matricesEqual(x: List<List<Double>>, y: List<List<Double>>, tol: Double): Boolean =
        x.indices.all { i -> x[i].indices.all { j -> kotlin.math.abs(x[i][j] - y[i][j]) <= tol } }

    /** 归一化：去空白、统一乘号、小写（保守，避免误判等价）。 */
    fun normalizeExpr(s: String): String = s.replace("\\s".toRegex(), "").replace("·", "*").lowercase()

    private fun numericallyEqual(
        accepted: List<String>,
        got: String,
        variable: String,
        samples: List<Double>,
        tolerance: Double,
        evaluator: ExpressionEvaluator,
    ): Boolean = accepted.any { acc ->
        samples.all { x ->
            val ev = evaluator.eval(acc, mapOf(variable to x))
            val gv = evaluator.eval(got, mapOf(variable to x))
            ev != null && gv != null && kotlin.math.abs(ev - gv) <= tolerance
        }
    }
}

/** 抽象求值器：把表达式字符串在给定变量取值下求数值。具体实现待选型（exp4j / Symja / ...）。 */
fun interface ExpressionEvaluator {
    /** 解析/求值失败返回 null。 */
    fun eval(expr: String, vars: Map<String, Double>): Double?
}
