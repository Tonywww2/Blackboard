package com.tonywww.blackboard.builtin

import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.BlackboardApi.id
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.question.Questions
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register
import com.tonywww.blackboard.validation.Validators
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource

/**
 * 内置题目生成器：加/减/乘/除/平方（`#blackboard:math`）与一个简单是非题（`#blackboard:text`）；
 * 均含 `#blackboard:default`，供默认黑板（`DEFAULT_TYPE` 的 `ByTag(DEFAULT)`）选题。
 * 判题复用 P1-D 的 [Validators]。在模组初始化阶段（注册表冻结前）调用 [register]。
 */
object BuiltinGenerators {

    val ADDITION: QuestionGenerator = mathGen(id("addition"), weight = 10) { r, d ->
        val hi = ceiling(99, 90, d)
        val a = r.nextIntBetweenInclusive(1, hi)
        val b = r.nextIntBetweenInclusive(1, hi)
        "$a + $b = ?" to (a + b)
    }

    val SUBTRACTION: QuestionGenerator = mathGen(id("subtraction"), weight = 10) { r, d ->
        val hi = ceiling(99, 90, d)
        val a = r.nextIntBetweenInclusive(1, hi)
        val b = r.nextIntBetweenInclusive(0, a)
        "$a - $b = ?" to (a - b)
    }

    val MULTIPLICATION: QuestionGenerator = mathGen(id("multiplication"), weight = 8) { r, d ->
        val hi = ceiling(12, 6, d)
        val a = r.nextIntBetweenInclusive(2, hi)
        val b = r.nextIntBetweenInclusive(2, hi)
        "$a * $b = ?" to (a * b)
    }

    val DIVISION: QuestionGenerator = mathGen(id("division"), weight = 6) { r, d ->
        val hi = ceiling(12, 6, d)
        val b = r.nextIntBetweenInclusive(2, hi)
        val q = r.nextIntBetweenInclusive(1, hi)
        "${b * q} / $b = ?" to q
    }

    val SQUARE: QuestionGenerator = mathGen(id("square"), weight = 5) { r, d ->
        val hi = ceiling(20, 12, d)
        val a = r.nextIntBetweenInclusive(2, hi)
        "$a^2 = ?" to (a * a)
    }

    val TRUE_FALSE_SUM: QuestionGenerator = run {
        val genId = id("true_false_sum")
        QuestionGenerator.builder(genId)
            .tag(BlackboardTags.TEXT, BlackboardTags.DEFAULT)
            .weight(5)
            .generate { ctx ->
                val r = ctx.random
                val a = r.nextIntBetweenInclusive(1, 50)
                val b = r.nextIntBetweenInclusive(1, 50)
                val correct = a + b
                val shown = if (r.nextBoolean()) correct else correct + r.nextIntBetweenInclusive(1, 5)
                val text = "Is $a + $b = $shown? (yes/no)"
                Questions.builder(genId)
                    .content(Component.literal(text))
                    .prompt(Component.literal(text))
                    .store("answer", if (shown == correct) "yes" else "no")
                    .build()
            }
            .validate(Validators.text())
            .build()
    }

    /** 所有内置生成器（保序）。 */
    val ALL: List<QuestionGenerator> =
        listOf(ADDITION, SUBTRACTION, MULTIPLICATION, DIVISION, SQUARE, TRUE_FALSE_SUM)

    /** 注册全部内置生成器到全局注册表（须在冻结前调用，通常由模组入口触发）。 */
    fun register() {
        ALL.forEach { BlackboardRegistries.QUESTION_GENERATORS.register(it) }
    }

    /** 按难度放宽操作数上限：难度 0 用 [base]，每升一级 +[perLevel]（难度取非负，兜底负难度）。 */
    private fun ceiling(base: Int, perLevel: Int, difficulty: Int): Int =
        base + perLevel * difficulty.coerceAtLeast(0)

    /** 构建一个 MATH 标签、用数值校验器（[Validators.number]）判题的算术生成器（题面随 `difficulty` 放宽）。 */
    private fun mathGen(
        genId: ResourceLocation,
        weight: Int,
        make: (RandomSource, Int) -> Pair<String, Int>,
    ): QuestionGenerator =
        QuestionGenerator.builder(genId)
            .tag(BlackboardTags.MATH, BlackboardTags.DEFAULT)
            .weight(weight)
            .generate { ctx ->
                val (text, answer) = make(ctx.random, ctx.difficulty.coerceAtLeast(0))
                Questions.builder(genId)
                    .content(Component.literal(text))
                    .prompt(Component.literal(text))
                    .store("answer", answer)
                    .build()
            }
            .validate(Validators.number())
            .build()
}
