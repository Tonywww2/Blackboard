package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.event.RegisterGeneratorsEvent
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.registry.SimpleRegistry
import com.tonywww.blackboard.api.registry.register
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * 题目生成器的注册与运行期热重载协调器。
 *
 * 两层模型：
 * - **启动基线**：内置/其他 mod 在 `init`、KubeJS 在 startup 直接写入
 *   [BlackboardRegistries.QUESTION_GENERATORS] 的生成器。首次 [initialLoad] 时快照，之后每次重建都原样
 *   恢复。KubeJS 启动脚本无法在运行期重跑，故其生成器由基线保留、**不随** `/blackboard reload` 刷新
 *   （改动需重启，属 KubeJS startup 限制）。
 * - **可重载层**：通过 [BlackboardEvents.REGISTER_GENERATORS] 贡献的生成器，每次 [initialLoad]/[reload]
 *   重新触发，可增/改（数据包驱动或 mod 的重载钩子）。
 *
 * 均在服务端主线程调用（重建期间注册表会短暂清空）。黑板类型（[BlackboardRegistries.BLACKBOARD_TYPES]）
 * 只在 [initialLoad] 冻结，热重载不动。
 */
object GeneratorReload {

    private val logger = LoggerFactory.getLogger("Blackboard/Reload")
    private val generators get() = BlackboardRegistries.QUESTION_GENERATORS

    /** 首次快照的启动基线（内置 + KubeJS + mod 在 init 直接注册者）；null = 尚未初始化。 */
    @Volatile
    private var baseline: List<QuestionGenerator>? = null

    /** 服务器启动：快照启动基线 → 触发 REGISTER_GENERATORS(INITIAL) → 冻结生成器与黑板类型。 */
    fun initialLoad(server: MinecraftServer?) {
        baseline = generators.all() // 此刻仅含直接注册者（可重载事件尚未触发）
        runRebuild(RegisterGeneratorsEvent.Reason.INITIAL, server)
        BlackboardRegistries.BLACKBOARD_TYPES.freeze()
    }

    /** `/blackboard reload`：仅重建生成器注册表（保留启动基线、重跑可重载层）。返回重载后的生成器总数。 */
    fun reload(server: MinecraftServer?): Int {
        if (baseline == null) baseline = generators.all() // 保险：理论上 initialLoad 已先行
        runRebuild(RegisterGeneratorsEvent.Reason.RELOAD, server)
        return generators.all().size
    }

    private fun runRebuild(reason: RegisterGeneratorsEvent.Reason, server: MinecraftServer?) {
        val base = baseline ?: emptyList()
        val event = RegisterGeneratorsEvent(reason, server)
        BlackboardEvents.REGISTER_GENERATORS.invoke(event)
        val disabled = event.disabled()
        val added = rebuildInto(generators, base, event.collected(), disabled)
        logger.info(
            "生成器{}：基线 {} + 可重载 {} - 禁用 {} = 共 {}",
            if (reason == RegisterGeneratorsEvent.Reason.INITIAL) "初始化" else "热重载",
            base.size,
            added,
            disabled.size,
            generators.all().size,
        )
    }

    /**
     * 纯重建逻辑（便于单测）：重开 [reg] → 恢复 [baseline] → 追加 [contributed] 中未与基线冲突者 → 冻结。
     * 返回追加的可重载生成器数量；与基线 id 冲突者跳过并告警（基线优先，不被覆盖）。
     */
    internal fun rebuildInto(
        reg: SimpleRegistry<QuestionGenerator>,
        baseline: List<QuestionGenerator>,
        contributed: List<QuestionGenerator>,
        disabled: Set<ResourceLocation> = emptySet(),
    ): Int {
        reg.reopen()
        for (gen in baseline) {
            if (gen.id in disabled) continue // 被 REGISTER_GENERATORS 事件禁用的基线生成器
            reg.register(gen)
        }
        var added = 0
        for (gen in contributed) {
            if (gen.id in disabled) continue
            if (reg.contains(gen.id)) {
                logger.warn("忽略与启动基线重复的生成器 id：{}", gen.id)
                continue
            }
            reg.register(gen)
            added++
        }
        reg.freeze()
        return added
    }
}
