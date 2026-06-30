package com.tonywww.blackboard.api.question

import net.minecraft.resources.ResourceLocation

/**
 * 题目生成器：函数式核心。出题（[generate]）与判题（[validate]）均为匿名函数。
 * 其他模组通过注册 [QuestionGenerator] 扩展题库。
 */
class QuestionGenerator private constructor(
    val id: ResourceLocation,
    /** 出题：匿名函数。 */
    val generate: (GenerationContext) -> Question,
    /** 判题：匿名函数。 */
    val validate: (Question, AnswerContext) -> AnswerResult,
    /** 默认权重（用于加权随机选题），不为负。 */
    val weight: Int,
    /** 标签，便于按池/分类筛选（如 `#blackboard:math`）。 */
    val tags: Set<ResourceLocation>,
) {
    class Builder(private val id: ResourceLocation) {
        private var generate: ((GenerationContext) -> Question)? = null
        private var validate: ((Question, AnswerContext) -> AnswerResult)? = null
        private var weight = 1
        private val tags = mutableSetOf<ResourceLocation>()

        fun generate(fn: (GenerationContext) -> Question) = apply { generate = fn }
        fun validate(fn: (Question, AnswerContext) -> AnswerResult) = apply { validate = fn }
        fun weight(w: Int) = apply { weight = w }
        fun tag(vararg t: ResourceLocation) = apply { tags += t }

        fun build(): QuestionGenerator {
            require(weight >= 0) { "weight 不能为负: $id" }
            return QuestionGenerator(
                id,
                requireNotNull(generate) { "generate(...) 未设置: $id" },
                requireNotNull(validate) { "validate(...) 未设置: $id" },
                weight,
                tags.toSet(),
            )
        }
    }

    companion object {
        fun builder(id: ResourceLocation) = Builder(id)
    }
}
