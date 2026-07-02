package com.tonywww.blackboard.builtin

import com.tonywww.blackboard.api.BlackboardApi.BlackboardTags
import com.tonywww.blackboard.api.BlackboardApi.id
import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.register
import com.tonywww.blackboard.chat.DefaultAnswerFormat
import com.tonywww.blackboard.core.defaultReward
import com.tonywww.blackboard.core.weightedRandomSelect

/**
 * 内置黑板类型。目前提供唯一的 [DEFAULT_TYPE]（id `blackboard:default`，internal-core-api §4.3）：
 *  - 选题池 `ByTag(DEFAULT)`：取 `#blackboard:default` 标签下的全部生成器；
 *  - 选题策略 [weightedRandomSelect]（按权重随机，见 `core/Selection.kt` §6）；
 *  - 答对回调 [defaultReward]（roll `rewards/default` 并广播 `RewardEvent`，见 `core/Reward.kt` §7）；
 *  - 作答格式 [DefaultAnswerFormat]（`!ans <boardId> <答案>`）；
 *  - `maxAttempts = 0`：不限作答次数。
 *
 * 在模组初始化阶段（注册表冻结前）调用 [register]，通常由模组入口（P7-B）触发。
 *
 * 注：`ByTag(DEFAULT)` 仅解析带 `#blackboard:default` 标签的生成器；内置生成器（P3-D）当前仅带
 * `MATH`/`TEXT` 标签，需补齐 DEFAULT 标签后该类型方有候选（跨任务协调项，见进度看板备注）。
 */
object BuiltinBlackboardTypes {

    /** 默认黑板类型：按权重随机出题、答对发默认奖励、`!ans` 作答、最多 2 次作答。 */
    val DEFAULT_TYPE: BlackboardType =
        BlackboardType.builder(id("default"))
            .pool(GeneratorPool.ByTag(BlackboardTags.DEFAULT))
            .selector(::weightedRandomSelect)
            .rewardLootTable(id("rewards/default"))
            .onSolved(::defaultReward)
            .answerFormat(DefaultAnswerFormat)
            .maxAttempts(2)
            .build()

    /** 所有内置黑板类型（保序）。 */
    val ALL: List<BlackboardType> = listOf(DEFAULT_TYPE)

    /** 注册全部内置黑板类型到全局注册表（须在冻结前调用，通常由模组入口触发）。 */
    fun register() {
        ALL.forEach { BlackboardRegistries.BLACKBOARD_TYPES.register(it) }
    }
}
