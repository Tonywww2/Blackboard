package com.tonywww.blackboard.validation

import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.api.question.QuestionBuilder
import com.tonywww.blackboard.api.question.Questions
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidatorsTest {

    private fun question(build: QuestionBuilder.() -> Unit): Question {
        val rid = ResourceLocation.tryParse("blackboard:t")!!
        return Questions.builder(rid).content(Component.literal("q")).apply(build).build()
    }

    /** 仅 [AnswerContext.text] 参与校验，其余成员在单测中不应被访问。 */
    private fun ctx(answer: String): AnswerContext = object : AnswerContext {
        override val player get() = throw UnsupportedOperationException()
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val text = answer
    }

    @Test
    fun `text validator ignores case and trims`() {
        val v = Validators.text()
        val q = question { store("answer", "Hello") }
        assertTrue(v(q, ctx("  hello ")) is AnswerResult.Correct)
        assertTrue(v(q, ctx("world")) is AnswerResult.Incorrect)
    }

    @Test
    fun `number validator distinguishes invalid from incorrect`() {
        val v = Validators.number()
        val q = question { store("answer", 42.0) }
        assertTrue(v(q, ctx("42")) is AnswerResult.Correct)
        assertTrue(v(q, ctx("3/2")) is AnswerResult.Incorrect) // 解析成功但答错
        assertTrue(v(q, ctx("abc")) is AnswerResult.Invalid) // 解析失败
    }

    @Test
    fun `matrix validator parses and compares`() {
        val v = Validators.matrix()
        val q = question { store("answer", "[[1,2],[3,4]]") }
        assertTrue(v(q, ctx("[[1, 2], [3, 4]]")) is AnswerResult.Correct)
        assertTrue(v(q, ctx("[[1,2],[3,5]]")) is AnswerResult.Incorrect)
        assertTrue(v(q, ctx("[[1,2,3],[4,5,6]]")) is AnswerResult.Incorrect) // 维度不符
        assertTrue(v(q, ctx("garbage")) is AnswerResult.Invalid)
    }

    @Test
    fun `matrix validator accepts latex answers`() {
        val v = Validators.matrix()
        val q = question { store("answer", "[[1,2],[3,4]]") }
        // pmatrix / bmatrix 等 LaTeX 写法皆可作答
        assertTrue(v(q, ctx("\\begin{pmatrix}1 & 2 \\\\ 3 & 4\\end{pmatrix}")) is AnswerResult.Correct)
        assertTrue(v(q, ctx("\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}")) is AnswerResult.Correct)
        // 值错 → Incorrect
        assertTrue(v(q, ctx("\\begin{pmatrix}1 & 2 \\\\ 3 & 5\\end{pmatrix}")) is AnswerResult.Incorrect)
        // 维度不符 → Incorrect
        assertTrue(v(q, ctx("\\begin{pmatrix}1 & 2 & 3\\end{pmatrix}")) is AnswerResult.Incorrect)
    }

    @Test
    fun `parseLatexMatrix handles column vector and frac`() {
        // 列向量 `\begin{pmatrix}5 \\ -6\end{pmatrix}`
        assertEquals(
            listOf(listOf(5.0), listOf(-6.0)),
            Validators.parseLatexMatrix("\\begin{pmatrix}5\\\\-6\\end{pmatrix}"),
        )
        // `\frac{1}{2}` 单元
        assertEquals(
            listOf(listOf(0.5, 2.0)),
            Validators.parseLatexMatrix("\\begin{bmatrix}\\frac{1}{2} & 2\\end{bmatrix}"),
        )
        // 非 LaTeX → null
        assertNull(Validators.parseLatexMatrix("[[1,2]]"))
    }
}
