package com.tonywww.blackboard.api.registry

import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.question.QuestionGenerator

/**
 * 模组自管理的注册表集合：加载器无关、可在 KubeJS 启动期注册、世界加载前冻结一致快照。
 *
 * 方块实体仅持久化 `generatorId` + `Question.data`，不绑定具体 lambda（见 internal-core-api §2/§10）。
 */
object BlackboardRegistries {
    @JvmField
    val QUESTION_GENERATORS = SimpleRegistry<QuestionGenerator>("question_generator")

    @JvmField
    val BLACKBOARD_TYPES = SimpleRegistry<BlackboardType>("blackboard_type")

    /** 注册阶段结束后冻结所有注册表，拒绝后续修改。 */
    fun freezeAll() {
        QUESTION_GENERATORS.freeze()
        BLACKBOARD_TYPES.freeze()
    }
}

/** 便捷注册：[QuestionGenerator] 自带 id 与 tags。 */
fun SimpleRegistry<QuestionGenerator>.register(gen: QuestionGenerator): QuestionGenerator =
    register(gen.id, gen, gen.tags)

/** 便捷注册：[BlackboardType] 自带 id。 */
fun SimpleRegistry<BlackboardType>.register(type: BlackboardType): BlackboardType =
    register(type.id, type)

/**
 * 把 [GeneratorPool] 解析为候选生成器列表（保序）。
 *
 * 定义在注册表侧（而非 `api/board/GeneratorPool.kt` 契约文件）以保持 `api.registry → api.board`
 * 单向依赖、避免互引；默认使用全局 [BlackboardRegistries.QUESTION_GENERATORS]。
 */
fun GeneratorPool.resolve(
    reg: SimpleRegistry<QuestionGenerator> = BlackboardRegistries.QUESTION_GENERATORS,
): List<QuestionGenerator> = when (this) {
    is GeneratorPool.ByTag -> reg.byTag(tag)
    is GeneratorPool.Explicit -> ids.mapNotNull { reg.get(it) }
    GeneratorPool.All -> reg.all()
}
