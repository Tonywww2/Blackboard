package com.tonywww.blackboard.api.question

import net.minecraft.network.chat.Component

/** 判题结果。 */
sealed interface AnswerResult {
    /** 正确。[score] 用于部分得分题（0..1）。 */
    data class Correct(val score: Double = 1.0, val feedback: Component? = null) : AnswerResult

    /** 错误（消耗一次作答机会）。 */
    data class Incorrect(val feedback: Component? = null) : AnswerResult

    /** 无法判定（格式不符等），不消耗作答机会。 */
    data class Invalid(val feedback: Component? = null) : AnswerResult

    companion object {
        @JvmStatic
        @JvmOverloads
        fun correct(score: Double = 1.0) = Correct(score)

        @JvmStatic
        fun incorrect() = Incorrect()

        @JvmStatic
        fun invalid() = Invalid()
    }
}
