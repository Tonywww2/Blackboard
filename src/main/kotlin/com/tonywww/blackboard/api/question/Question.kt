package com.tonywww.blackboard.api.question

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * 一道已生成的题目实例，可随方块实体序列化持久化。
 *
 * 由 [Questions.builder] 构建；判题逻辑通过 [data] 读取标准答案等信息。
 */
interface Question {
    /** 生成它的题目生成器 ID。 */
    val generatorId: ResourceLocation

    /** 题面内容（数学题可含 LaTeX），直接交给渲染接口 / AUI 渲染。 */
    val content: Component

    /** 生成器自定义的持久化数据（标准答案、随机种子、参数等）。 */
    val data: CompoundTag

    /** 可选：用于聊天/日志的纯文本题面。 */
    val prompt: Component?

    fun getInt(key: String): Int
    fun getDouble(key: String): Double
    fun getString(key: String): String
    fun getBoolean(key: String): Boolean
}
