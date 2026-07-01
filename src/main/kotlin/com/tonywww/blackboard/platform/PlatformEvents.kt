package com.tonywww.blackboard.platform

import com.tonywww.blackboard.api.BlackboardApi
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.chat.ChatHandler
import com.tonywww.blackboard.content.BlackboardBlockEntity
import com.tonywww.blackboard.core.BlackboardManager
import com.tonywww.blackboard.core.GeneratorReload
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.HitResult
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
 * 注册方式因加载器而异：
 * - Forge：用 KLF 的 [EventBusSubscriber] 自动扫描，按方法参数的事件类型判定所属总线（kotlinlangforge §3）。
 * - NeoForge：KLF 的 langprovider 位于 PLUGIN 模块层，无法读取 GAME 层的 `NeoForge.EVENT_BUS`
 *   （其 AutomaticEventSubscriber.getGameBus 会抛 IllegalAccessError），故这里不加注解，改由
 *   [Blackboard] 在 mod 构造时（GAME 层代码）把本对象手动注册到游戏总线。
 *
 * 两版方法体共享；监听方法不可为 `private`。仅 import 因加载器而异（用 Stonecutter 隔离）。
 *
 * 注：冻结时机由本任务（P7-A）统一负责；P7-B 不碰冻结。
 */
//? if forge {
@EventBusSubscriber
//?}
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

    /** 注册指令（权限等级 2）：`/blackboard reload` 热重载生成器；`settype`/`generate` 作用于玩家注视的黑板。 */
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
                )
                .then(
                    // /blackboard settype <typeId> — 把注视的黑板改为指定类型并重新出题（让自定义类型/池可在世界内使用）。
                    Commands.literal("settype").then(
                        Commands.argument("type", ResourceLocationArgument.id()).executes { ctx ->
                            val typeId = ResourceLocationArgument.getId(ctx, "type")
                            if (!BlackboardRegistries.BLACKBOARD_TYPES.contains(typeId)) {
                                ctx.source.sendFailure(Component.literal("Blackboard: unknown blackboard type $typeId"))
                                return@executes 0
                            }
                            val be = lookedAtBoard(ctx.source) ?: return@executes 0
                            be.setBlackboardType(typeId)
                            val q = BlackboardManager.generateQuestion(be, ctx.source.player)
                            ctx.source.sendSuccess(
                                {
                                    Component.literal(
                                        "Blackboard: set type to $typeId" +
                                            if (q == null) " (no question could be generated)" else "",
                                    )
                                },
                                true,
                            )
                            1
                        },
                    ),
                )
                .then(
                    // /blackboard generate — 重新为注视的黑板出题（若未分配类型则先给默认类型）。
                    Commands.literal("generate").executes { ctx ->
                        val be = lookedAtBoard(ctx.source) ?: return@executes 0
                        if (be.blackboardTypeId == null) be.setBlackboardType(BlackboardApi.DEFAULT_TYPE_ID)
                        val q = BlackboardManager.generateQuestion(be, ctx.source.player)
                        ctx.source.sendSuccess(
                            {
                                Component.literal(
                                    if (q != null) "Blackboard: regenerated question"
                                    else "Blackboard: failed (no candidate generator?)",
                                )
                            },
                            true,
                        )
                        if (q != null) 1 else 0
                    },
                ),
        )
    }

    /**
     * 服务端指令辅助：射线定位玩家正在注视的黑板方块实体；未注视方块 / 目标非黑板时向来源报错并返回 null。
     */
    private fun lookedAtBoard(source: CommandSourceStack): BlackboardBlockEntity? {
        val player = source.playerOrException
        val level = player.level()
        val reach = 6.0
        val eye = player.getEyePosition(1.0f)
        val look = player.getViewVector(1.0f)
        val end = eye.add(look.x * reach, look.y * reach, look.z * reach)
        val hit = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (hit.type != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Blackboard: look at a blackboard first"))
            return null
        }
        val be = level.getBlockEntity(hit.blockPos) as? BlackboardBlockEntity
        if (be == null) source.sendFailure(Component.literal("Blackboard: the target is not a blackboard"))
        return be
    }

    /** 区块加载：登记其中的黑板，供 boardId 定位（P5-A 委托 P7-A 接线）。 */
    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        for (be in chunk.blockEntities.values) {
            if (be !is BlackboardBlockEntity) continue
            if (be.boardId.isEmpty()) {
                // 世界生成放置的黑板尚未初始化：回主线程分配 boardId+默认类型并出题（区块加载可能在异步线程）。
                level.server.execute { if (!be.isRemoved) BlackboardManager.onPlaced(be) }
            } else {
                BlackboardManager.track(level, be)
            }
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
