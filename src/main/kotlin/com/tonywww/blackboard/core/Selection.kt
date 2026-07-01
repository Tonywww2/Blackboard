package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.SelectionContext
import com.tonywww.blackboard.api.board.WeightedGenerator
import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.event.SelectGeneratorEvent
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.SimpleRegistry
import com.tonywww.blackboard.api.registry.resolve
import net.minecraft.util.RandomSource

/**
 * 选题主流程（internal-core-api §6 的权威实现）：
 *
 * 1. 由 [BlackboardType.pool] 解析候选并包装为 [WeightedGenerator]；
 * 2. 广播 [BlackboardEvents.SELECT_GENERATOR]，开发者可增删候选、改权重，或设置 `forced`；
 * 3. 若事件设置了 `forced` 则直接返回；
 * 4. 候选为空（未注册任何生成器 / 事件清空了候选）→ 返回 `null`，交由调用方优雅处理（不抛异常）；
 * 5. 否则交给 [BlackboardType.selector]（默认 [weightedRandomSelect]）。
 *
 * @param registry 候选来源，默认全局 [BlackboardRegistries.QUESTION_GENERATORS]（单测可注入）。
 */
fun selectGenerator(
    type: BlackboardType,
    ctx: SelectionContext,
    registry: SimpleRegistry<QuestionGenerator> = BlackboardRegistries.QUESTION_GENERATORS,
): QuestionGenerator? {
    val candidates = type.pool.resolve(registry)
        .map { WeightedGenerator(it, it.weight) }
        .toMutableList()

    val event = SelectGeneratorEvent(ctx, candidates)
    BlackboardEvents.SELECT_GENERATOR.invoke(event)

    event.forced?.let { return it }
    if (event.candidates.isEmpty()) return null // 无候选（题库空/被清空）——避免崩溃，返回 null
    return type.selector(event.candidates, ctx)
}

/**
 * 默认选题策略：按权重随机。用 `ctx.level.random`（`RandomSource`）以保证服务端确定性与可复现。
 * 该函数签名与 [BlackboardType.selector] 一致，可用 `::weightedRandomSelect` 直接传入。
 */
fun weightedRandomSelect(candidates: List<WeightedGenerator>, ctx: SelectionContext): QuestionGenerator =
    pickWeighted(candidates, ctx.level.random)

/**
 * 加权随机的纯逻辑实现（与 MC 世界解耦，便于单测）：
 * 权重 `<= 0` 视为不可选；当全部非正时回退为等概率挑选。
 */
internal fun pickWeighted(candidates: List<WeightedGenerator>, random: RandomSource): QuestionGenerator {
    require(candidates.isNotEmpty()) { "没有候选生成器" }
    val pool = candidates.filter { it.weight > 0 }
    if (pool.isEmpty()) return candidates[random.nextInt(candidates.size)].generator
    val total = pool.sumOf { it.weight }
    var roll = random.nextInt(total)
    for (wg in pool) {
        roll -= wg.weight
        if (roll < 0) return wg.generator
    }
    return pool.last().generator
}
