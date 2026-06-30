package com.tonywww.blackboard.core

import com.tonywww.blackboard.api.board.RewardContext
import com.tonywww.blackboard.api.event.BlackboardEvents
import com.tonywww.blackboard.api.event.RewardEvent
import com.tonywww.blackboard.platform.PlatformLoot

/**
 * 默认「答对发奖」逻辑，供 `DEFAULT_TYPE.onSolved` 引用，其它黑板类型亦可复用。
 *
 * 流程（internal-core-api §7，采用「`lootTable` 作为是否再 roll 的开关」的简化规则）：
 * 1. 以黑板类型的 `rewardLootTable` 作为 [RewardEvent] 的初始 `lootTable`，`extraDrops` 初始为空。
 * 2. 广播 [BlackboardEvents.REWARD]：监听器可将 `lootTable` 置 null（取消默认发放）或替换为其它表，
 *    并向 `extraDrops` 追加物品，实现自定义奖励。
 * 3. 事件后：若 `lootTable != null` 则 roll 一次并入，连同 `extraDrops` 一并发放（背包满则掉落于玩家处）。
 *
 * 「roll 战利品」「给予/掉落」等平台相关操作封装在 [PlatformLoot]，故本文件本身无 Stonecutter 分支。
 */
fun defaultReward(rc: RewardContext) {
    val event = RewardEvent(
        rc.level,
        rc.pos,
        rc.blockState,
        rc.player,
        rc.question,
        rc.result,
        lootTable = rc.blackboard.rewardLootTable,
        extraDrops = mutableListOf(),
    )
    BlackboardEvents.REWARD.invoke(event)

    val drops = buildList {
        event.lootTable?.let { addAll(PlatformLoot.rollLootTable(rc.level, rc.player, rc.pos, it)) }
        addAll(event.extraDrops)
    }
    drops.forEach { PlatformLoot.giveOrDrop(rc.player, it) }
}
