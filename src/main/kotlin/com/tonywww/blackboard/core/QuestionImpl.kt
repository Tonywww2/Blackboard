package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.question.Question
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/** [Question] 的内部参考实现；便捷读取直接落到 [data]。 */
internal class QuestionImpl(
    override val generatorId: ResourceLocation,
    override val content: Component,
    override val data: CompoundTag,
    override val prompt: Component?,
) : Question {
    override fun getInt(key: String) = data.getInt(key)
    override fun getDouble(key: String) = data.getDouble(key)
    override fun getString(key: String) = data.getString(key)
    override fun getBoolean(key: String) = data.getBoolean(key)
}
