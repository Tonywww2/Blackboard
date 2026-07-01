package com.tonywww.blackboard.api.question

import com.tonywww.blackboard.core.QuestionImpl
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * [Question] 的构建入口。
 *
 * 命名为 `Questions`（而非 `Question.builder`）以避免与 [Question] 接口同名冲突，
 * 详见 internal-core-api §4.1 修订说明。
 */
object Questions {
    @JvmStatic
    fun builder(id: ResourceLocation): QuestionBuilder = QuestionBuilder(id)
}

/** 题目构建器，供 [QuestionGenerator] 的 `generate` 函数内使用。 */
class QuestionBuilder(private val generatorId: ResourceLocation) {
    private var content: Component? = null
    private var prompt: Component? = null
    private val data = CompoundTag()

    fun content(c: Component) = apply { content = c }
    fun prompt(c: Component) = apply { prompt = c }

    fun store(key: String, v: Int) = apply { data.putInt(key, v) }
    fun store(key: String, v: Double) = apply { data.putDouble(key, v) }
    fun store(key: String, v: String) = apply { data.putString(key, v) }
    fun store(key: String, v: Boolean) = apply { data.putBoolean(key, v) }

    fun build(): Question = QuestionImpl(
        generatorId,
        requireNotNull(content) { "Question.content 未设置: $generatorId" },
        data,
        prompt,
    )
}
