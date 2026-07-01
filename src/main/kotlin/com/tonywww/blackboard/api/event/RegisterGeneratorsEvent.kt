package com.tonywww.blackboard.api.event

import com.tonywww.blackboard.api.question.QuestionGenerator
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer

/**
 * 在 [BlackboardEvents.REGISTER_GENERATORS] 上广播：贡献**可热重载**的 [QuestionGenerator]。
 *
 * 监听器用 [register] 提交生成器；协调器（`core.GeneratorReload`）在事件触发后统一写入全局注册表。
 *
 * 想支持 `/blackboard reload` 热重载的扩展方（数据包加载器、mod 的重载钩子）应在**此事件**注册，
 * 而非在模组 `init` 里直接写注册表——后者只在启动期生效、被快照为「启动基线」保留，但不随命令刷新。
 *
 * @property reason 触发原因：首次启动 [Reason.INITIAL] 或命令热重载 [Reason.RELOAD]。
 * @property server 当前服务器（可空：极早期阶段可能尚无）。
 */
class RegisterGeneratorsEvent(val reason: Reason, val server: MinecraftServer?) {

    /** 触发原因。 */
    enum class Reason { INITIAL, RELOAD }

    private val collected = ArrayList<QuestionGenerator>()
    private val disabled = LinkedHashSet<ResourceLocation>()

    /** 提交一个生成器。若其 id 已由启动基线提供，协调器会忽略它并告警。 */
    fun register(generator: QuestionGenerator) {
        collected += generator
    }

    /**
     * 按 id 禁用（删除）一个生成器，本次重建生效：无论它来自启动基线（内置 / 其他 mod / KubeJS
     * startup）还是其他贡献者，协调器都会跳过它。每次 `/blackboard reload` 会重新触发本事件，
     * 故需持续禁用者应在每次事件里都调用。
     */
    fun disable(id: ResourceLocation) {
        disabled += id
    }

    /** [disable] 的别名，语义为「删除这个已注册的生成器」。 */
    fun remove(id: ResourceLocation) = disable(id)

    /** 供协调器读取本次收集到的生成器（保序）。 */
    internal fun collected(): List<QuestionGenerator> = collected

    /** 供协调器读取本次被禁用的生成器 id。 */
    internal fun disabled(): Set<ResourceLocation> = disabled
}
