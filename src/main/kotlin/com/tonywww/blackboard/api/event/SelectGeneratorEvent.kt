package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.board.SelectionContext
import com.tonywww.blackboard.api.board.WeightedGenerator
import com.tonywww.blackboard.api.question.QuestionGenerator

/**
 * 出题前广播：监听器可修改 [candidates]（增、删、改权重）、设置 [forced] 直接指定生成器（跳过加权随机），
 * 或改写 [difficulty] 动态调整本次难度。见 design §6.1 / internal-core-api §6。
 */
class SelectGeneratorEvent(
    val context: SelectionContext,
    /** 当前候选（含权重）。监听器可增、删、改权重。 */
    val candidates: MutableList<WeightedGenerator>,
    /**
     * 本次出题难度，初值 = 全局配置难度基数 + 黑板类型增量（已夹到 `0..10`）。监听器可按上下文（玩家、
     * 位置、时间等）改写；出题流程会把最终值再夹到 `0..10` 后传给生成器。默认 0（用于脱离出题流程直接构造，如单测）。
     */
    var difficulty: Int = 0,
) {
    /** 设置后跳过加权随机，直接使用该生成器。 */
    var forced: QuestionGenerator? = null
}
