package com.tonywww.blackboard.builtin.calculus

import com.tonywww.blackboard.api.BlackboardApi
import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.AnswerResult
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
 * 微积分模块 · 装配为 [QuestionGenerator]（三类拆分之「装配」）。
 *
 * 阶段一：数值答案题型（G/I/J）——`content`=题面 LaTeX、`prompt`=infix 兜底、`data["answer"]`=整数答案，
 * 判题直接用 [Validators.number]。生成器打 `#blackboard:calculus` 标签；配套内置黑板类型 [CALCULUS_TYPE]
 * 用 `ByTag(CALCULUS)` 选题。入口 `Blackboard.kt` 在冻结前调 [register] + [registerType]。
 */
object CalculusGenerators {

    private fun id(path: String) = BlackboardApi.id(path)

    val DERIV_AT_POINT: QuestionGenerator =
        numericGen(id("calc_deriv_at"), weight = 6) { r, d -> CalculusProblems.derivativeAtPoint(r, d) }

    val DEF_INTEGRAL: QuestionGenerator =
        numericGen(id("calc_def_integral"), weight = 5) { r, d -> CalculusProblems.definiteIntegral(r, d) }

    val LIMIT: QuestionGenerator =
        numericGen(id("calc_limit"), weight = 5) { r, d -> CalculusProblems.limit(r, d) }

    val DIFFERENTIATION: QuestionGenerator =
        symbolicGen(id("calc_differentiation"), weight = 6) { r, d -> CalculusProblems.differentiation(r, d) }

    val INDEF_INTEGRAL: QuestionGenerator =
        symbolicGen(id("calc_indef_integral"), weight = 4) { r, d -> CalculusProblems.indefiniteIntegral(r, d) }

    /** 所有微积分生成器（数值 G/I/J + 符号求导/不定积分）。 */
    val ALL: List<QuestionGenerator> =
        listOf(DERIV_AT_POINT, DEF_INTEGRAL, LIMIT, DIFFERENTIATION, INDEF_INTEGRAL)

    /** 内置微积分黑板类型：`ByTag(CALCULUS)` 选题、默认发奖、`!ans` 作答、不限次。 */
    val CALCULUS_TYPE: BlackboardType =
        BlackboardType.builder(id("calculus"))
            .pool(GeneratorPool.ByTag(BlackboardTags.CALCULUS))
            .selector(::weightedRandomSelect)
            .onSolved(::defaultReward)
            .rewardLootTable(id("rewards/default"))
            .answerFormat(DefaultAnswerFormat)
            .maxAttempts(0)
            .build()

    /** 注册全部微积分生成器（须在注册表冻结前调用）。 */
    fun register() {
        ALL.forEach { BlackboardRegistries.QUESTION_GENERATORS.register(it) }
    }

    /** 注册微积分黑板类型（须在冻结前、且在 [register] 之后调用）。 */
    fun registerType() {
        BlackboardRegistries.BLACKBOARD_TYPES.register(CALCULUS_TYPE)
    }

    // ---- 内部 ----

    private fun numericGen(
        gid: ResourceLocation,
        weight: Int,
        make: (RandomSource, Int) -> CalculusProblem,
    ): QuestionGenerator =
        QuestionGenerator.builder(gid)
            .tag(BlackboardTags.CALCULUS)
            .weight(weight)
            .generate { ctx -> toQuestion(gid, make(ctx.random, ctx.difficulty)) }
            .validate(Validators.number("answer", 1e-6))
            .build()

    private fun toQuestion(gid: ResourceLocation, p: CalculusProblem): Question =
        Questions.builder(gid)
            .content(Component.literal(p.stemLatex))
            .prompt(Component.literal(p.stemInfix))
            .store("answer", p.answerNumber!!)
            .build()

    /** 符号题（`EXPRESSION`）生成器：答案存 infix 字符串，判题走抽样等价（积分两点差消 +C）。 */
    private fun symbolicGen(
        gid: ResourceLocation,
        weight: Int,
        make: (RandomSource, Int) -> CalculusProblem,
    ): QuestionGenerator =
        QuestionGenerator.builder(gid)
            .tag(BlackboardTags.CALCULUS)
            .weight(weight)
            .generate { ctx -> toQuestionSymbolic(gid, make(ctx.random, ctx.difficulty)) }
            .validate(::validateSymbolic)
            .build()

    private fun toQuestionSymbolic(gid: ResourceLocation, p: CalculusProblem): Question =
        Questions.builder(gid)
            .content(Component.literal(p.stemLatex))
            .prompt(Component.literal(p.stemInfix))
            .store("answer", p.answerInfix!!)
            .store("lo", p.sampleLo)
            .store("hi", p.sampleHi)
            .store("intg", p.integral)
            .build()

    /**
     * `EXPRESSION` 判题：玩家输入先 `normalizeLatex` 再 `parse`，解析失败→`Invalid`（不消耗机会）；
     * 积分题用 [sampleEqualUpToConst] 消 +C，其余用 [sampleEqual] 逐点比较；不等价→`Incorrect`。
     */
    private fun validateSymbolic(q: Question, a: AnswerContext): AnswerResult {
        val std = parseExpr(q.getString("answer")) ?: return AnswerResult.invalid()
        val got = parseExpr(normalizeLatex(a.text)) ?: return AnswerResult.invalid()
        val lo = q.getDouble("lo")
        val hi = q.getDouble("hi")
        val intg = q.getBoolean("intg")
        val equal = if (intg) sampleEqualUpToConst(std, got, lo, hi) else sampleEqual(std, got, lo, hi)
        return if (equal) AnswerResult.correct() else AnswerResult.incorrect()
    }
}
