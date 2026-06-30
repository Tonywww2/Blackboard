package com.tonywww.blackboard.api.event

/**
 * 黑板事件总线聚合：每种事件一个 [EventHook]。
 *
 * 约定（internal-core-api §3）：所有事件携带黑板方块的 `level`/`pos`/`blockState`；
 * [AnswerEvent]/[RewardEvent] 额外携带 `player`。
 */
object BlackboardEvents {
    /** 出题前广播：可增删候选/改权重，或强制指定生成器。 */
    val SELECT_GENERATOR = EventHook<SelectGeneratorEvent>("select_generator")

    /** 题目生成后广播：可读取/二次处理（统计、日志、改写渲染）。 */
    val QUESTION_GENERATED = EventHook<QuestionGeneratedEvent>("question_generated")

    /** 作答判定后广播。 */
    val ANSWER = EventHook<AnswerEvent>("answer")

    /** 答对发奖前广播：可增删奖励物品或替换战利品表。 */
    val REWARD = EventHook<RewardEvent>("reward")
}
