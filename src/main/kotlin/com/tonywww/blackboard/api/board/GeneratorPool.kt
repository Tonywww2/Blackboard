package com.tonywww.blackboard.api.board

import net.minecraft.resources.ResourceLocation

/**
 * 候选生成器来源。
 *
 * 注：`GeneratorPool.resolve(reg)` 扩展定义在 `api/registry/BlackboardRegistries.kt`（P2-A），
 * 以保持 `api.registry → api.board` 单向依赖、避免互相引用。
 */
sealed interface GeneratorPool {
    /** 取某标签下全部生成器。 */
    data class ByTag(val tag: ResourceLocation) : GeneratorPool

    /** 显式 id 集合。 */
    data class Explicit(val ids: List<ResourceLocation>) : GeneratorPool

    /** 全部已注册生成器。 */
    data object All : GeneratorPool
}
