package com.tonywww.blackboard.api.board

import com.tonywww.blackboard.api.chat.AnswerFormat
import com.tonywww.blackboard.api.question.AnswerContext
import com.tonywww.blackboard.api.question.QuestionGenerator
import net.minecraft.resources.ResourceLocation

/**
 * 描述「这块黑板**怎么行为**」：选题池、选题策略、奖励、作答格式等。
 * 其他模组通过注册新的 [BlackboardType] 创建行为不同的黑板。
 *
 * `selector` 采用 internal-core-api §6 的**权威签名**：接收（已被事件修改的）候选列表与上下文，
 * 而非 design §5.5 的单参示意。
 *
 * 注：答对后销毁方块不在此处，而在 `BlackboardBlockEntity.onSolved`（默认发奖 + 销毁，可重写）。
 */
class BlackboardType private constructor(
    val id: ResourceLocation,

    /** 候选生成器来源（按标签或显式集合）。 */
    val pool: GeneratorPool,

    /** 选题策略：从候选中选一个。默认按权重随机（由内置类型注入）。 */
    val selector: (candidates: List<WeightedGenerator>, ctx: SelectionContext) -> QuestionGenerator,

    /** 答对回调：默认发放 [rewardLootTable] 并广播 RewardEvent。 */
    val onSolved: (RewardContext) -> Unit,

    /** 答错回调：默认无。 */
    val onFailed: (AnswerContext) -> Unit,

    /** 默认奖励战利品表。 */
    val rewardLootTable: ResourceLocation?,

    /** 作答格式解析器。 */
    val answerFormat: AnswerFormat,

    /** 每题最大作答次数；<=0 表示不限。 */
    val maxAttempts: Int,

    /**
     * 相对全局难度基数（[com.tonywww.blackboard.BlackboardConfig.difficultyBase]）的难度增量。
     * 有效难度 = (基数 + 本增量)，再夹到 `0..10`；默认 0（跟随全局），可为负（本类型更简单）。
     */
    val difficultyModifier: Int = 0,
) {
    class Builder(private val id: ResourceLocation) {
        private var pool: GeneratorPool? = null
        private var selector: ((List<WeightedGenerator>, SelectionContext) -> QuestionGenerator)? = null
        private var onSolved: ((RewardContext) -> Unit)? = null
        private var onFailed: (AnswerContext) -> Unit = {}
        private var rewardLootTable: ResourceLocation? = null
        private var answerFormat: AnswerFormat? = null
        private var maxAttempts: Int = 0
        private var difficultyModifier: Int = 0

        fun pool(p: GeneratorPool) = apply { pool = p }
        fun selector(fn: (List<WeightedGenerator>, SelectionContext) -> QuestionGenerator) = apply { selector = fn }
        fun onSolved(fn: (RewardContext) -> Unit) = apply { onSolved = fn }
        fun onFailed(fn: (AnswerContext) -> Unit) = apply { onFailed = fn }
        fun rewardLootTable(table: ResourceLocation?) = apply { rewardLootTable = table }
        fun answerFormat(fmt: AnswerFormat) = apply { answerFormat = fmt }
        fun maxAttempts(n: Int) = apply { maxAttempts = n }

        /** 相对全局难度基数的增量（可正可负，默认 0；见 [BlackboardType.difficultyModifier]）。 */
        fun difficultyModifier(modifier: Int) = apply { difficultyModifier = modifier }

        fun build(): BlackboardType = BlackboardType(
            id,
            requireNotNull(pool) { "pool(...) 未设置: $id" },
            requireNotNull(selector) { "selector(...) 未设置: $id" },
            requireNotNull(onSolved) { "onSolved(...) 未设置: $id" },
            onFailed,
            rewardLootTable,
            requireNotNull(answerFormat) { "answerFormat(...) 未设置: $id" },
            maxAttempts,
            difficultyModifier,
        )
    }

    companion object {
        @JvmStatic
        fun builder(id: ResourceLocation) = Builder(id)
    }
}
