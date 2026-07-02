package com.tonywww.blackboard.platform

import com.tonywww.blackboard.api.BlackboardApi
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.chat.ChatHandler
import com.tonywww.blackboard.content.BlackboardBlockEntity
import com.tonywww.blackboard.core.BlackboardManager
import com.tonywww.blackboard.core.GeneratorReload
import com.mojang.brigadier.arguments.IntegerArgumentType
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

    /** 服务端聊天 → 作答路由；命中作答时拦截原 `!ans …` 行（改由 [ChatHandler] 广播包装后的回答）。 */
    @SubscribeEvent
    fun onServerChat(event: ServerChatEvent) {
        if (ChatHandler.handle(event.player, event.rawText)) {
            event.isCanceled = true
        }
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
                            { Component.translatable("command.blackboard.reloaded", count) },
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
                                ctx.source.sendFailure(Component.translatable("command.blackboard.unknown_type", typeId.toString()))
                                return@executes 0
                            }
                            val be = lookedAtBoard(ctx.source) ?: return@executes 0
                            be.setBlackboardType(typeId)
                            val q = BlackboardManager.generateQuestion(be, ctx.source.player)
                            ctx.source.sendSuccess(
                                {
                                    val msg = Component.empty()
                                        .append(Component.translatable("command.blackboard.set_type", typeId.toString()))
                                    if (q == null) msg.append(Component.translatable("command.blackboard.regen_failed_suffix"))
                                    msg
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
                                if (q != null) Component.translatable("command.blackboard.regenerated")
                                else Component.translatable("command.blackboard.generate_failed")
                            },
                            true,
                        )
                        if (q != null) 1 else 0
                    },
                )
                .then(
                    // /blackboard difficulty <0..10> | clear — 设置/清除注视黑板的每板难度覆盖并重新出题。
                    Commands.literal("difficulty")
                        .then(
                            Commands.argument("value", IntegerArgumentType.integer(0, 10)).executes { ctx ->
                                setBoardDifficulty(ctx.source, IntegerArgumentType.getInteger(ctx, "value"))
                            },
                        )
                        .then(
                            Commands.literal("clear").executes { ctx -> setBoardDifficulty(ctx.source, null) },
                        ),
                ),
        )
    }

    /**
     * 设置（或用 `null` 清除）注视黑板的每板难度覆盖并重新出题，向来源反馈；未注视黑板返回 0。
     */
    private fun setBoardDifficulty(source: CommandSourceStack, value: Int?): Int {
        val be = lookedAtBoard(source) ?: return 0
        if (be.blackboardTypeId == null) be.setBlackboardType(BlackboardApi.DEFAULT_TYPE_ID)
        be.setDifficultyOverride(value)
        val q = BlackboardManager.generateQuestion(be, source.player)
        source.sendSuccess(
            {
                val label = if (value != null) Component.translatable("command.blackboard.difficulty_set", value)
                else Component.translatable("command.blackboard.difficulty_cleared")
                val suffix = if (q == null) Component.translatable("command.blackboard.regen_failed_suffix")
                else Component.translatable("command.blackboard.regen_ok_suffix")
                Component.empty().append(label).append(suffix)
            },
            true,
        )
        return if (q != null) 1 else 0
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
            source.sendFailure(Component.translatable("command.blackboard.look_first"))
            return null
        }
        val be = level.getBlockEntity(hit.blockPos) as? BlackboardBlockEntity
        if (be == null) source.sendFailure(Component.translatable("command.blackboard.not_a_board"))
        return be
    }

    /** 区块加载：登记其中已初始化的黑板，供 boardId 定位（未初始化/世界生成的黑板由 `BlackboardBlockEntity.onLoad` 处理）。 */
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
