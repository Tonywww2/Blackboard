package com.tonywww.blackboard.data

import com.google.common.hash.Hashing
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tonywww.blackboard.api.BlackboardApi
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import java.util.concurrent.CompletableFuture
//? if forge {
import net.minecraftforge.data.event.GatherDataEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
//?} else {
/*import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.data.event.GatherDataEvent
*///?}

/**
 * 数据生成（datagen）：由 `runData` 触发；KLF `@EventBusSubscriber` 自动挂到 mod 总线。
 *
 * 目前生成：
 * - `#blackboard:blackboards` 方块标签（聚合本模组黑板方块——其它 mod 的黑板方块也可加入此标签，
 *   便于"数据驱动地识别黑板"）；
 * - `#blackboard:has_blackboard` 群系标签（世界生成放置黑板的允许群系白名单，默认 = 全体主世界群系）。
 *
 * 说明：[com.tonywww.blackboard.api.question.QuestionGenerator] 的分类标签（`math`/`text`/`default`）属
 * **模组内部注册表**（`SimpleRegistry` 的标签索引），不是 MC 数据标签，故仍在代码里用 `.tag(...)` 设置，
 * 不由 datagen 生成。仅 [GatherDataEvent] 相关 import 因加载器而异（Stonecutter 隔离），provider 逻辑共享。
 */
@EventBusSubscriber
object BlackboardDataGen {

    @SubscribeEvent
    fun gather(event: GatherDataEvent) {
        event.generator.addProvider(
            event.includeServer(),
            DataProvider.Factory { output -> BlackboardBlockTagProvider(output) },
        )
        event.generator.addProvider(
            event.includeServer(),
            DataProvider.Factory { output -> BlackboardBiomeTagProvider(output) },
        )
    }
}

/**
 * 直接写原始 JSON 的方块标签 provider——避开各版本 `TagsProvider` / `DataProvider.saveStable` 的签名差异，
 * 仅用 vanilla 的 [CachedOutput]/[PackOutput]/[DataProvider] 与 Guava 哈希（两版一致）。
 */
private class BlackboardBlockTagProvider(private val output: PackOutput) : DataProvider {

    override fun run(cache: CachedOutput): CompletableFuture<*> {
        val json = JsonObject().apply {
            addProperty("replace", false)
            add("values", JsonArray().apply { add("${BlackboardApi.MOD_ID}:blackboard") })
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val path = output.getOutputFolder(PackOutput.Target.DATA_PACK)
            .resolve("${BlackboardApi.MOD_ID}/$TAG_FOLDER/blackboards.json")
        return CompletableFuture.runAsync {
            cache.writeIfNeeded(path, bytes, Hashing.sha256().hashBytes(bytes))
        }
    }

    override fun getName(): String = "Blackboard Block Tags"

    private companion object {
        // 1.21 起数据包注册表目录 blocks→block。
        //? if forge {
        const val TAG_FOLDER = "tags/blocks"
        //?} else {
        /*const val TAG_FOLDER = "tags/block"
        *///?}
    }
}

/**
 * 生成群系白名单标签 `#blackboard:has_blackboard`——世界生成放置黑板的**允许群系**（默认 = 全体主世界群系，
 * 可被数据包覆盖以增删允许群系）。群系标签目录 `tags/worldgen/biome` 两版一致（不受 1.21 blocks→block
 * 目录改名影响），故无需 Stonecutter 分支。
 */
private class BlackboardBiomeTagProvider(private val output: PackOutput) : DataProvider {

    override fun run(cache: CachedOutput): CompletableFuture<*> {
        val json = JsonObject().apply {
            addProperty("replace", false)
            add("values", JsonArray().apply { add("#minecraft:is_forest") })
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val path = output.getOutputFolder(PackOutput.Target.DATA_PACK)
            .resolve("${BlackboardApi.MOD_ID}/tags/worldgen/biome/has_blackboard.json")
        return CompletableFuture.runAsync {
            cache.writeIfNeeded(path, bytes, Hashing.sha256().hashBytes(bytes))
        }
    }

    override fun getName(): String = "Blackboard Biome Tags"
}
