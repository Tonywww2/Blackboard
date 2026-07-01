package com.tonywww.blackboard.builtin.logic

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
 * 逻辑值运算模块 · 装配为 [QuestionGenerator]（仿 `builtin/calculus/CalculusGenerators`）。
 *
 * 两个生成器：[EVALUATION]（常量求值 `logic_eval`）与 [ASSIGNMENT]（变量赋值 `logic_assign`），
 * 均打 `#blackboard:logic` 标签，判题走宽松 [Validators.boolean]。配套内置黑板类型 [LOGIC_TYPE]。
 * 入口 `Blackboard.kt` 在冻结前调 [registerReloadable] + [registerType]。
 */
object LogicGenerators {

    private fun id(path: String) = BlackboardApi.id(path)

    val EVALUATION: QuestionGenerator = boolGen(id("logic_eval")) { r, d -> LogicProblems.evaluation(r, d) }

    val ASSIGNMENT: QuestionGenerator = boolGen(id("logic_assign")) { r, d -> LogicProblems.assignment(r, d) }

    /** 两个逻辑生成器（常量求值 + 变量赋值）。 */
    val ALL: List<QuestionGenerator> = listOf(EVALUATION, ASSIGNMENT)

    /** 内置逻辑黑板类型：`ByTag(LOGIC)` 选题、默认发奖、`!ans` 作答、不限次。 */
    val LOGIC_TYPE: BlackboardType =
        BlackboardType.builder(id("logic"))
            .pool(GeneratorPool.ByTag(BlackboardTags.LOGIC))
            .selector(::weightedRandomSelect)
            .onSolved(::defaultReward)
            .rewardLootTable(id("rewards/default"))
            .answerFormat(DefaultAnswerFormat)
            .maxAttempts(0)
            .build()

    /**
     * 将逻辑生成器注册到**可热重载层**（[BlackboardEvents.REGISTER_GENERATORS]）。
     *
     * 与微积分同理：需读配置 [BlackboardConfig.logicInDefaultPool]（mod 构造期尚未加载），故走事件层——
     * 服务器启动 / `/blackboard reload` 时触发。开启时给每个生成器追加 `#blackboard:default` 标签（保留 `#blackboard:logic`）。
     */
    fun registerReloadable() {
        BlackboardEvents.REGISTER_GENERATORS.register { event ->
            val inDefault = BlackboardConfig.logicInDefaultPool.get()
            for (gen in ALL) event.register(if (inDefault) withDefaultTag(gen) else gen)
        }
    }

    /** 注册逻辑黑板类型（须在冻结前、且在 [registerReloadable] 之后调用）。 */
    fun registerType() {
        BlackboardRegistries.BLACKBOARD_TYPES.register(LOGIC_TYPE)
    }

    // ---- 内部 ----

    /** 克隆生成器并追加 `#blackboard:default` 标签（其余不变）。 */
    private fun withDefaultTag(gen: QuestionGenerator): QuestionGenerator =
        QuestionGenerator.builder(gen.id)
            .tag(*gen.tags.toTypedArray(), BlackboardTags.DEFAULT)
            .weight(gen.weight)
            .generate(gen.generate)
            .validate(gen.validate)
            .build()

    /** 构建一个 LOGIC 标签、用宽松布尔校验器（[Validators.boolean]）判题的逻辑生成器。 */
    private fun boolGen(gid: ResourceLocation, make: (RandomSource, Int) -> LogicProblem): QuestionGenerator =
        QuestionGenerator.builder(gid)
            .tag(BlackboardTags.LOGIC)
            .weight(6)
            .generate { ctx ->
                val p = make(ctx.random, ctx.difficulty)
                Questions.builder(gid)
                    .content(Component.literal(p.stemLatex))
                    .prompt(Component.literal(p.stemInfix))
                    .store("answer", if (p.answer) "true" else "false")
                    .build()
            }
            .validate(Validators.boolean())
            .build()
}
