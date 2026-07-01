package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.GeneratorPool
import com.tonywww.blackboard.api.board.SelectionContext
import com.tonywww.blackboard.api.board.WeightedGenerator
import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.QuestionGenerator
import com.tonywww.blackboard.api.registry.SimpleRegistry
import com.tonywww.blackboard.api.registry.register
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectionTest {

    private fun gen(path: String): QuestionGenerator =
        QuestionGenerator.builder(ResourceLocation.tryParse("blackboard:$path")!!)
            .generate { throw UnsupportedOperationException("not used in this test") }
            .validate { _, _ -> AnswerResult.incorrect() }
            .build()

    /** 选题不访问坐标/方块/玩家，这些成员在单测中应当不被触碰。 */
    private fun selCtx(): SelectionContext = object : SelectionContext {
        override val blackboard get() = throw UnsupportedOperationException()
        override val level get() = throw UnsupportedOperationException()
        override val pos get() = throw UnsupportedOperationException()
        override val blockState get() = throw UnsupportedOperationException()
        override val player get() = null
    }

    private fun typeWith(
        pool: GeneratorPool,
        selector: (List<WeightedGenerator>, SelectionContext) -> QuestionGenerator,
    ): BlackboardType =
        BlackboardType.builder(ResourceLocation.tryParse("blackboard:test_type")!!)
            .pool(pool)
            .selector(selector)
            .onSolved { }
            .answerFormat { _ -> null }
            .build()

    @Test
    fun `weighted random respects weights and excludes non-positive`() {
        val a = gen("a")
        val b = gen("b")
        val c = gen("c")
        val candidates = listOf(WeightedGenerator(a, 1), WeightedGenerator(b, 3), WeightedGenerator(c, 0))
        val random = RandomSource.create(12345L)

        val counts = HashMap<String, Int>()
        repeat(10_000) {
            val picked = pickWeighted(candidates, random)
            counts.merge(picked.id.path, 1, Int::plus)
        }

        assertEquals(0, counts.getOrDefault("c", 0)) // weight 0 never chosen
        assertTrue(counts.getValue("b") > counts.getValue("a")) // weight 3 favoured over weight 1
    }

    @Test
    fun `weighted random falls back to uniform when all weights non-positive`() {
        val a = gen("a")
        val b = gen("b")
        val candidates = listOf(WeightedGenerator(a, 0), WeightedGenerator(b, -1))
        val picked = pickWeighted(candidates, RandomSource.create(1L))
        assertTrue(picked === a || picked === b)
    }

    @Test
    fun `forced generator from event wins over selector`() {
        val forced = gen("forced_one")
        val reg = SimpleRegistry<QuestionGenerator>("test")
        reg.register(gen("forced_pool_marker"))
        // 仅对本测试的标记候选生效，避免污染其它测试。
        BlackboardEvents.SELECT_GENERATOR.register { e ->
            if (e.candidates.any { it.generator.id.path == "forced_pool_marker" }) e.forced = forced
        }
        val type = typeWith(GeneratorPool.All) { c, _ -> c.first().generator }
        assertSame(forced, selectGenerator(type, selCtx(), reg))
    }

    @Test
    fun `selector chooses from resolved candidates when not forced`() {
        val x = gen("pick_x")
        val y = gen("pick_y")
        val reg = SimpleRegistry<QuestionGenerator>("test")
        reg.register(x)
        reg.register(y)
        val type = typeWith(GeneratorPool.All) { c, _ -> c.last().generator }
        assertSame(y, selectGenerator(type, selCtx(), reg)) // last in registration order
    }

    @Test
    fun `empty pool returns null instead of throwing`() {
        val reg = SimpleRegistry<QuestionGenerator>("test") // 空注册表 = 用户移除了全部生成器
        val type = typeWith(GeneratorPool.All) { c, _ -> c.first().generator }
        assertNull(selectGenerator(type, selCtx(), reg))
    }
}
