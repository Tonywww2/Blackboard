package com.tonywww.blackboard.platform

import com.tonywww.blackboard.chat.ChatHandler
import com.tonywww.blackboard.content.BlackboardBlockEntity
import com.tonywww.blackboard.core.BlackboardManager
import com.tonywww.blackboard.core.GeneratorReload
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.LevelChunk
//? if forge {
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
//?} else {
/*import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
*///?}

/**
 * 平台（加载器）事件订阅：把服务端事件接到本模组逻辑。C4（[ServerChatEvent]）见 loader-platform-api §6。
 *
 * KLF 自动扫描 [EventBusSubscriber] 标注的类，并按方法参数的事件类型判定其所属总线
 * （kotlinlangforge §3），故这里无需手动注册、也无需区分 mod / game 总线；监听方法不可为 `private`。
 * 仅 import 因加载器而异（用 Stonecutter 隔离），方法体两版共享。
 *
 * 注：冻结时机由本任务（P7-A）统一负责；P7-B 不碰冻结。
 */
@EventBusSubscriber
object PlatformEvents {

    /** 服务端聊天 → 作答路由（§13(2)：当前不拦截原聊天，仅旁路解析）。 */
    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {
        ChatHandler.handle(event.player, event.rawText)
    }

    /** 服务器即将启动：注册阶段（含 KubeJS startup）已结束——快照启动基线、触发可重载层、冻结注册表（internal-core-api §10）。 */
    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        GeneratorReload.initialLoad(event.server)
    }

    /** 服务器停止：清空黑板索引。 */
    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        BlackboardManager.clearAll()
    }

    /** 注册指令：`/blackboard reload` 热重载题目生成器（权限等级 2）。 */
    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(
            Commands.literal("blackboard").requires { it.hasPermission(2) }
                .then(
                    Commands.literal("reload").executes { ctx ->
                        val count = GeneratorReload.reload(ctx.source.server)
                        ctx.source.sendSuccess(
                            { Component.literal("Blackboard: reloaded question generators ($count total)") },
                            true,
                        )
                        count
                    },
                ),
        )
    }

    /** 区块加载：登记其中的黑板，供 boardId 定位（P5-A 委托 P7-A 接线）。 */
    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        for (be in chunk.blockEntities.values) {
            if (be is BlackboardBlockEntity) BlackboardManager.track(level, be)
        }
    }

    /** 区块卸载：注销其中的黑板。 */
    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        for (be in chunk.blockEntities.values) {
            if (be is BlackboardBlockEntity) BlackboardManager.untrack(level, be)
        }
    }
}
