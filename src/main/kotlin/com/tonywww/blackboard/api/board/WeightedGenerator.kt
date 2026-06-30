package com.tonywww.blackboard.api.board

import com.tonywww.blackboard.api.question.QuestionGenerator

/** 带权重的候选生成器（用于加权随机选题；权重可被选题事件修改）。 */
data class WeightedGenerator(val generator: QuestionGenerator, var weight: Int)
