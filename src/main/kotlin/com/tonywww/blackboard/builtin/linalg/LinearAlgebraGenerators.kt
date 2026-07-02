package com.tonywww.blackboard.builtin.linalg

import com.tonywww.blackboard.BlackboardConfig
import com.tonywww.blackboard.api.BlackboardApi
import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.question.Questions
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register
import com.tonywww.blackboard.chat.DefaultAnswerFormat
import com.tonywww.blackboard.core.defaultReward
import com.tonywww.blackboard.core.weightedRandomSelect
import com.tonywww.blackboard.validation.Validators
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource

/**
 * 线性代数模块 · 装配为 [QuestionGenerator]。
 *
 * 点积 [DOT] 答案为整数（判题走 [Validators.number]）；矩阵求值(解 Ax=b) [EVAL] / 矩阵-向量积 [MATVEC] /
 * 逆矩阵 [INVERSE] 答案为矩阵（`[[a,b],[c,d]]` 串，判题走 [Validators.matrix]；列向量写作 `[[x],[y]]`）。
 * 题面 `content`=矩阵 LaTeX（交黑板渲染），`prompt`=纯文本兜底。
 *
 * 生成器打 `#blackboard:linear_algebra` 标签；配套内置黑板类型 [LINEAR_ALGEBRA_TYPE] 用
 * `ByTag(LINEAR_ALGEBRA)` 选题。入口 `Blackboard.kt` 在冻结前调 [registerReloadable] + [registerType]。
 */
object LinearAlgebraGenerators {

    private fun id(path: String) = BlackboardApi.id(path)

    val DOT: QuestionGenerator =
        numericGen(id("la_dot"), weight = 6) { r, d -> LinearAlgebraProblems.dotProduct(r, d) }

    val EVAL: QuestionGenerator =
        matrixGen(id("la_eval"), weight = 6) { r, d -> LinearAlgebraProblems.matrixEval(r, d) }

    val MATVEC: QuestionGenerator =
        matrixGen(id("la_matvec"), weight = 6) { r, d -> LinearAlgebraProblems.matrixVector(r, d) }

    val INVERSE: QuestionGenerator =
        matrixGen(id("la_inverse"), weight = 4) { r, d -> LinearAlgebraProblems.inverse(r, d) }

    val SINGULAR: QuestionGenerator =
        matrixGen(id("la_singular"), weight = 5) { r, d -> LinearAlgebraProblems.singularValues(r, d) }

    /** 所有线性代数生成器（保序）。 */
    val ALL: List<QuestionGenerator> = listOf(DOT, EVAL, MATVEC, INVERSE, SINGULAR)

    /** 内置线性代数黑板类型：`ByTag(LINEAR_ALGEBRA)` 选题、默认发奖、`!ans` 作答、最多 2 次作答。 */
    val LINEAR_ALGEBRA_TYPE: BlackboardType =
        BlackboardType.builder(id("linear_algebra"))
            .pool(GeneratorPool.ByTag(BlackboardTags.LINEAR_ALGEBRA))
            .selector(::weightedRandomSelect)
            .onSolved(::defaultReward)
            .rewardLootTable(id("rewards/default"))
            .answerFormat(DefaultAnswerFormat)
            .maxAttempts(2)
            .build()

    /**
     * 将线性代数生成器注册到**可热重载层**（[BlackboardEvents.REGISTER_GENERATORS]，同微积分）。
     *
     * 走事件层而非 init 直接写基线：需读配置 [BlackboardConfig.linearAlgebraInDefaultPool]，而该配置在 mod
     * 构造期尚未加载；本事件在服务器启动（配置已加载）时触发，`/blackboard reload` 也会重跑——改配置后重载
     * 即可让线性代数题目即时进/出默认池。开启时给每个生成器追加 `#blackboard:default` 标签（保留 `#blackboard:linear_algebra`）。
     */
    fun registerReloadable() {
        BlackboardEvents.REGISTER_GENERATORS.register { event ->
            val inDefault = BlackboardConfig.linearAlgebraInDefaultPool.get()
            for (gen in ALL) event.register(if (inDefault) withDefaultTag(gen) else gen)
        }
    }

    /** 克隆生成器并追加 `#blackboard:default` 标签（其余不变）。 */
    private fun withDefaultTag(gen: QuestionGenerator): QuestionGenerator =
        QuestionGenerator.builder(gen.id)
            .tag(*gen.tags.toTypedArray(), BlackboardTags.DEFAULT)
            .weight(gen.weight)
            .generate(gen.generate)
            .validate(gen.validate)
            .build()

    /** 注册线性代数黑板类型（须在冻结前、且在 [registerReloadable] 之后调用）。 */
    fun registerType() {
        BlackboardRegistries.BLACKBOARD_TYPES.register(LINEAR_ALGEBRA_TYPE)
    }

    // ---- 内部 ----

    private fun numericGen(
        gid: ResourceLocation,
        weight: Int,
        make: (RandomSource, Int) -> LinAlgProblem,
    ): QuestionGenerator =
        QuestionGenerator.builder(gid)
            .tag(BlackboardTags.LINEAR_ALGEBRA)
            .weight(weight)
            .generate { ctx -> toQuestion(gid, make(ctx.random, ctx.difficulty)) }
            .validate(Validators.number("answer", 1e-6))
            .build()

    private fun matrixGen(
        gid: ResourceLocation,
        weight: Int,
        make: (RandomSource, Int) -> LinAlgProblem,
    ): QuestionGenerator =
        QuestionGenerator.builder(gid)
            .tag(BlackboardTags.LINEAR_ALGEBRA)
            .weight(weight)
            .generate { ctx -> toQuestion(gid, make(ctx.random, ctx.difficulty)) }
            .validate(Validators.matrix("answer", 1e-6))
            .build()

    private fun toQuestion(gid: ResourceLocation, p: LinAlgProblem): Question {
        val b = Questions.builder(gid)
            .content(Component.literal(p.stemLatex))
            .prompt(Component.literal(p.stemInfix))
        when (p.answerKind) {
            LinAlgAnswerKind.NUMBER -> b.store("answer", p.answerNumber!!.toDouble())
            LinAlgAnswerKind.MATRIX -> b.store("answer", p.answerMatrix!!)
        }
        return b.build()
    }
}
