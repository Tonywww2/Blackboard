package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.board.SelectionContext
import com.tonywww.blackboard.api.board.WeightedGenerator
import com.tonywww.blackboard.api.question.QuestionGenerator

/**
 * 出题前广播：监听器可修改 [candidates]（增、删、改权重），或设置 [forced] 直接指定生成器
 * （跳过加权随机）。见 design §6.1 / internal-core-api §6。
 */
class SelectGeneratorEvent(
    val context: SelectionContext,
    /** 当前候选（含权重）。监听器可增、删、改权重。 */
    val candidates: MutableList<WeightedGenerator>,
) {
    /** 设置后跳过加权随机，直接使用该生成器。 */
    var forced: QuestionGenerator? = null
}
