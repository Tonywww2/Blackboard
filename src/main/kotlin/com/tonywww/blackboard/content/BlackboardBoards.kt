package com.tonywww.blackboard.content

import com.tonywww.blackboard.api.BlackboardApi
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import java.util.function.Supplier

/**
 * 扩展点：注册**额外的黑板方块**并把它绑定到某个黑板类型。
 *
 * 放置这些方块时，[com.tonywww.blackboard.core.BlackboardManager.onPlaced] 会按方块 id 查到绑定的类型
 * 并出题（未绑定者回退到 [BlackboardApi.DEFAULT_TYPE_ID]）。所有注册须在**注册表事件之前**发生——
 * mod 在构造期调用，KubeJS 在 startup 脚本调用（此时方块/物品 `DeferredRegister` 尚未结算）。
 *
 * 这些方块与基础黑板共用 [BlackboardBlockEntity]（[ModBlockEntities] 会自动把它们纳入类型有效方块集）。
 * 方块的 blockstate/模型/贴图/lang 由注册方自备（本类只负责注册与类型绑定）。
 */
object BlackboardBoards {

    // 类型有效方块集（种子 = 基础黑板）；[ModBlockEntities] 用它构建 BlockEntityType。
    private val boardBlocks = mutableListOf<Supplier<out Block>>(ModBlocks.BLACKBOARD)

    // 展示到创造物品栏的黑板物品（种子 = 基础黑板物品）。
    private val boardItems = mutableListOf<Supplier<Item>>(ModItems.BLACKBOARD)

    // 方块 id → 黑板类型 id 绑定（基础黑板不在表中，回退默认类型）。
    private val blockToType = mutableMapOf<ResourceLocation, ResourceLocation>()

    /**
     * 注册一个 `blackboard:<path>` 命名空间下的新黑板方块（含 `BlockItem`），放置时默认绑定到 [typeId]。
     * 须在注册表事件之前调用（mod 构造期 / KubeJS startup）。
     */
    @JvmStatic
    @JvmOverloads
    fun register(
        path: String,
        typeId: ResourceLocation,
        props: BlockBehaviour.Properties = defaultProps(),
    ): Supplier<Block> {
        val block: Supplier<Block> = ModBlocks.REGISTER.register(path, Supplier<Block> { BlackboardBlock(props) })
        val item: Supplier<Item> = ModItems.REGISTER.register(path, Supplier<Item> { BlockItem(block.get(), Item.Properties()) })
        boardBlocks += block
        boardItems += item
        blockToType[BlackboardApi.id(path)] = typeId
        return block
    }

    /** KubeJS/脚本便捷重载：[typeId] 传 `"namespace:path"` 字符串。 */
    @JvmStatic
    fun register(path: String, typeId: String): Supplier<Block> =
        register(path, requireNotNull(ResourceLocation.tryParse(typeId)) { "非法的类型 id：$typeId" }, defaultProps())

    /**
     * 绑定一个**已由外部（如某 mod 自己的命名空间）注册**的黑板方块到 [typeId]，并纳入 BE 类型有效集。
     * 该方块须是返回 [BlackboardBlockEntity] 的 [BlackboardBlock]（或其子类）。物品/资源由注册方自理。
     */
    @JvmStatic
    fun bind(block: Supplier<out Block>, blockId: ResourceLocation, typeId: ResourceLocation) {
        boardBlocks += block
        blockToType[blockId] = typeId
    }

    /** 查某方块 id 绑定的黑板类型；未绑定返回 `null`（调用方回退默认类型）。 */
    internal fun typeFor(blockId: ResourceLocation): ResourceLocation? = blockToType[blockId]

    /** 全部黑板方块（基础 + 扩展），供 [ModBlockEntities] 构建类型有效集（在方块结算后调用）。 */
    internal fun validBlocks(): Array<Block> = boardBlocks.map { it.get() }.toTypedArray()

    /** 全部黑板物品（基础 + 扩展），供创造物品栏展示。 */
    internal fun tabItems(): List<Supplier<Item>> = boardItems

    private fun defaultProps(): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f)
}
