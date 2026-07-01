package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.BlackboardApi.id
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.SimpleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 校验 [GeneratorReload.rebuildInto] 的热重载语义（纯逻辑，不依赖 MC 运行时）：
 * 启动基线保留、可重载层可增改、重复 id 跳过、[SimpleRegistry.reopen] 正确清空并解冻。
 */
class GeneratorReloadTest {

    // generate/validate 在重建期不会被调用，用 error(...) 占位即可（返回 Nothing，兼容任意返回类型）。
    private fun gen(name: String, vararg tagNames: String): QuestionGenerator =
        QuestionGenerator.builder(id(name))
            .weight(1)
            .apply { tagNames.forEach { tag(id(it)) } }
            .generate { error("unused") }
            .validate { _, _ -> error("unused") }
            .build()

    @Test
    fun `rebuild preserves baseline, adds reloadable layer, and is re-runnable`() {
        val reg = SimpleRegistry<QuestionGenerator>("test")
        val base = gen("builtin", "math")
        val ext1 = gen("ext1")

        // 首次：基线 + 可重载 ext1。
        val added1 = GeneratorReload.rebuildInto(reg, listOf(base), listOf(ext1))
        assertEquals(1, added1)
        assertTrue(reg.isFrozen(), "重建后应冻结")
        assertEquals(setOf(id("builtin"), id("ext1")), reg.ids().toSet())
        assertEquals(listOf(base), reg.byTag(id("math")), "基线标签索引应保留")

        // 热重载：基线保留，可重载层新增 ext2（模拟运行期新定义的生成器）。
        val ext2 = gen("ext2")
        val added2 = GeneratorReload.rebuildInto(reg, listOf(base), listOf(ext1, ext2))
        assertEquals(2, added2)
        assertEquals(setOf(id("builtin"), id("ext1"), id("ext2")), reg.ids().toSet())
    }

    @Test
    fun `reopen clears entries and unfreezes so rebuild starts clean`() {
        val reg = SimpleRegistry<QuestionGenerator>("test")
        GeneratorReload.rebuildInto(reg, listOf(gen("stale")), emptyList())
        assertTrue(reg.contains(id("stale")))

        // 下一次重建：旧的 stale 不在基线里，reopen 应清掉它。
        GeneratorReload.rebuildInto(reg, listOf(gen("fresh")), emptyList())
        assertFalse(reg.contains(id("stale")), "reopen 应清空上一轮内容")
        assertTrue(reg.contains(id("fresh")))
    }

    @Test
    fun `reloadable id colliding with baseline is skipped, baseline wins`() {
        val reg = SimpleRegistry<QuestionGenerator>("test")
        val base = gen("dup")
        val dup = gen("dup") // 与基线同 id
        val ok = gen("ok")

        val added = GeneratorReload.rebuildInto(reg, listOf(base), listOf(dup, ok))
        assertEquals(1, added, "仅 ok 被追加")
        assertEquals(setOf(id("dup"), id("ok")), reg.ids().toSet())
        assertSame(base, reg.get(id("dup")), "基线保留、不被同 id 的可重载项覆盖")
    }

    @Test
    fun `disabled ids are skipped from both baseline and contributed`() {
        val reg = SimpleRegistry<QuestionGenerator>("test")
        val baseKeep = gen("keep")
        val baseDrop = gen("drop_base") // 基线中被禁用
        val extDrop = gen("drop_ext")   // 可重载层中被禁用
        val extKeep = gen("ext_keep")

        val added = GeneratorReload.rebuildInto(
            reg,
            baseline = listOf(baseKeep, baseDrop),
            contributed = listOf(extDrop, extKeep),
            disabled = setOf(id("drop_base"), id("drop_ext")),
        )
        assertEquals(1, added, "仅 ext_keep 被追加（drop_ext 被禁用）")
        assertEquals(setOf(id("keep"), id("ext_keep")), reg.ids().toSet())
        assertFalse(reg.contains(id("drop_base")), "被禁用的基线生成器应被跳过")
    }
}
