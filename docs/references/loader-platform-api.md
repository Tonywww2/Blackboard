# 外部参考：加载器/版本平台 API（Forge 1.20.1 ↔ NeoForge 1.21.1）

> 来源：FarmersDelight 真实源码——`1.20` 分支（Forge 1.20.1）与 `1.21` 分支（NeoForge 1.21.x），已逐文件核实。
> 用途：填补 [internal-core-api.md](internal-core-api.md) 中的 **【平台边界】**，并指导 `com.tonywww.blackboard.platform` / `content` 的 Stonecutter 条件实现。
> Stonecutter 注释语法见 [stonecutter.md](stonecutter.md)。

目录：
1. 方块 / 物品 / 方块实体注册（核实）
2. 方块实体客户端同步 `SyncedBlockEntity`（核实）
3. NBT 读写的 `HolderLookup.Provider` 变化（核实）
4. 网络通道（核实）——以及本项目是否需要
5. 入口与 mod 总线
6. 仍需确认：ServerChatEvent / 发奖 / Component 编解码
7. 映射到 Blackboard

---

## 1. 方块 / 物品 / 方块实体注册（核实）

**Forge 1.20.1**（`net.minecraftforge.registries.*`，holder 为 `RegistryObject`）：

```java
// ModBlocks.java (FarmersDelight 1.20)
public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
public static final RegistryObject<Block> STOVE =
        BLOCKS.register("stove", () -> new StoveBlock(Block.Properties.copy(Blocks.BRICKS)...));

// ModBlockEntityTypes.java (FarmersDelight 1.20)
public static final DeferredRegister<BlockEntityType<?>> TILES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
public static final RegistryObject<BlockEntityType<StoveBlockEntity>> STOVE =
        TILES.register("stove", () -> BlockEntityType.Builder.of(StoveBlockEntity::new, ModBlocks.STOVE.get()).build(null));
```

**NeoForge 1.21.x**（`net.neoforged.neoforge.registries.*`，holder 为 `Supplier` / `DeferredHolder`，registry 句柄为 `net.minecraft.core.registries.Registries`）：

```java
// ModBlocks.java (FarmersDelight 1.21)
public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, MODID);
public static final Supplier<Block> STOVE =
        BLOCKS.register("stove", () -> new StoveBlock(Block.Properties.ofFullCopy(Blocks.BRICKS)...));

// ModBlockEntityTypes.java (FarmersDelight 1.21)
public static final DeferredRegister<BlockEntityType<?>> TILES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
public static final Supplier<BlockEntityType<StoveBlockEntity>> STOVE =
        TILES.register("stove", () -> BlockEntityType.Builder.of(StoveBlockEntity::new, ModBlocks.STOVE.get()).build(null));
```

**核实到的差异**：

| 项 | Forge 1.20.1 | NeoForge 1.21.x |
| --- | --- | --- |
| `DeferredRegister` 包 | `net.minecraftforge.registries.DeferredRegister` | `net.neoforged.neoforge.registries.DeferredRegister` |
| 注册表句柄 | `ForgeRegistries.BLOCKS` / `BLOCK_ENTITY_TYPES` / `ITEMS` | `Registries.BLOCK` / `BLOCK_ENTITY_TYPE` / `ITEM`（`net.minecraft.core.registries.Registries`）|
| 返回 holder | `RegistryObject<T>` | `Supplier<T>` 或 `DeferredHolder<R,T>` |
| 取值 | `.get()` | `.get()` |
| 属性拷贝 | `Block.Properties.copy(...)` | `Block.Properties.ofFullCopy(...)` |
| `getCloneItemStack` 形参 | `BlockGetter` | `LevelReader` |
| `BlockEntityType.Builder.of(...).build(null)` | 相同 | 相同 |

> **统一技巧**：`RegistryObject<T>` 与 NeoForge 的 holder 都实现 `java.util.function.Supplier<T>`。因此字段统一声明为 `Supplier<T>`，只用 Stonecutter 隔离「import + `DeferredRegister.create(<句柄>, MODID)` 的句柄那一行」。

Kotlin + Stonecutter 写法（Blackboard）：

```kotlin
//? if forge {
/*import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
*///?} else {
import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister
//?}
import java.util.function.Supplier

object ModBlocks {
    val BLOCKS: DeferredRegister<Block> = DeferredRegister.create(
        //? if forge {
        /*ForgeRegistries.BLOCKS,
        *///?} else {
        Registries.BLOCK,
        //?}
        Blackboard.MOD_ID,
    )

    val BLACKBOARD: Supplier<Block> = BLOCKS.register("blackboard") { BlackboardBlock(blockProps()) }
}
```

> 属性 `copy` vs `ofFullCopy` 用字符串替换隔离更省事：`stonecutter { replacements.string(current.parsed >= "1.20.5") { replace("Block.Properties.copy(", "Block.Properties.ofFullCopy(") } }`（方向自定，落地验证）。

---

## 2. 方块实体客户端同步 `SyncedBlockEntity`（核实）

FarmersDelight 用一个基类承载同步样板（两个分支都叫 `SyncedBlockEntity`）：

**Forge 1.20.1**：
```java
public class SyncedBlockEntity extends BlockEntity {
    @Nullable @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) { load(packet.getTag()); }
    protected void inventoryChanged() {
        super.setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
}
```

**NeoForge 1.21.x**：
```java
public class SyncedBlockEntity extends BlockEntity {
    @Nullable @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    // onDataPacket 不再需要重写（默认实现已处理）
    protected void inventoryChanged() {
        super.setChanged();
        if (level != null) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
}
```

**差异**：
- `getUpdateTag()`（1.20）→ `getUpdateTag(HolderLookup.Provider registries)`（1.21），实现 `saveWithoutMetadata()` → `saveWithoutMetadata(registries)`。
- 1.20 需重写 `onDataPacket(Connection, packet)` 调 `load(packet.getTag())`；1.21 默认实现已处理，无需重写。
- `getUpdatePacket()` 与 `inventoryChanged()` 两版相同。

> **对 Blackboard 的意义**：黑板方块实体继承此基类，调用 `inventoryChanged()`（或等价的「内容变化」方法）即可把题面随 `getUpdateTag` 同步到客户端——**渲染所需的 `content` 走原版方块实体更新通道即可，通常无需自定义网络包**（见 §4）。

---

## 3. NBT 读写的 `HolderLookup.Provider` 变化（核实/推断）

由 §2 的 `getUpdateTag(HolderLookup.Provider)` / `saveWithoutMetadata(registries)` 可确认：**1.20.5+ 起，方块实体的存取 NBT 接口普遍新增 `HolderLookup.Provider registries` 形参**。

- 1.20.1：`saveAdditional(CompoundTag tag)` / `load(CompoundTag tag)`
- 1.21.x：`saveAdditional(CompoundTag tag, HolderLookup.Provider registries)` / `loadAdditional(CompoundTag tag, HolderLookup.Provider registries)`

> 方法名（`load` vs `loadAdditional`）与精确签名**落地时以编译器为准并回写**；但「新增 registries 形参」这一点已由 `getUpdateTag` 证实。用 Stonecutter 行/闭合注释隔离两版的 override 签名即可。

---

## 4. 网络通道（核实）

**Forge 1.20.1 — `SimpleChannel`**：
```java
public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MODID, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

public static void register() {
    int i = 0;
    INSTANCE.registerMessage(i++, FlipSkilletMessage.class,
            FlipSkilletMessage::encode, FlipSkilletMessage::new, FlipSkilletMessage::handle);
}
// 消息：ctor(FriendlyByteBuf) / encode(FriendlyByteBuf) / handle(Supplier<NetworkEvent.Context>)
//   handle 内：context.get().enqueueWork(() -> {...}); context.get().setPacketHandled(true);
```

**NeoForge 1.21.x — `PayloadRegistrar`（事件驱动）**：
```java
@EventBusSubscriber(modid = MODID)
public class ModNetworking {
    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RichSoilBoostParticlesPayload.TYPE, RichSoilBoostParticlesPayload.STREAM_CODEC, ClientPayloadHandler::handle...);
        registrar.playToServer(FlipSkilletPayload.TYPE, FlipSkilletPayload.STREAM_CODEC, ServerPayloadHandler::handle...);
    }
    // payload 为 CustomPacketPayload，含 TYPE (CustomPacketPayload.Type) 与 STREAM_CODEC (StreamCodec)
    // handler 形如 (Payload payload, IPayloadContext context) -> { context.player()... }
}
```

**差异**：
| 项 | Forge 1.20.1 | NeoForge 1.21.x |
| --- | --- | --- |
| 通道 | `NetworkRegistry.newSimpleChannel` | `RegisterPayloadHandlersEvent` → `event.registrar(ver)` |
| 注册 | `INSTANCE.registerMessage(id, Class, encode, decode, handle)` | `registrar.playToClient/playToServer(TYPE, STREAM_CODEC, handler)` |
| 包定义 | 普通类 + `encode/decode/handle` | `CustomPacketPayload` + `TYPE` + `STREAM_CODEC` |
| 上下文 | `Supplier<NetworkEvent.Context>` | `IPayloadContext`（`context.player()`）|
| 线程 | `context.enqueueWork(...)` | NeoForge 默认主线程回调 |

> **对 Blackboard 的意义（重要）**：黑板的题面同步走 §2 的方块实体更新通道即可，**作答反馈用聊天消息**（`player.sendSystemMessage(...)`）即可，因此**核心功能很可能完全不需要自定义网络包**。若后续需要（如客户端主动请求重渲染），再按上表实现，并用 `//? if forge {`/`//? if neoforge {` 整类隔离。

---

## 5. 入口与 mod 总线

- `DeferredRegister` 需绑定到 **mod 事件总线**：`BLOCKS.register(modBus)`、`TILES.register(modBus)`、`ITEMS.register(modBus)`。
- 取 mod 总线：
  - Forge 1.20.1：`FMLJavaModLoadingContext.get().getModEventBus()`，或经 KLF 用 `@Mod` 构造器参数 `IEventBus`。
  - NeoForge 1.21.1：`@Mod` 构造器参数 `IEventBus`。
- 经 **KotlinLangForge**（见 [kotlinlangforge.md](kotlinlangforge.md)）两版统一用构造器参数 `IEventBus`，在其中调用各 `DeferredRegister.register(bus)`。

---

## 6. 仍需确认（FarmersDelight 未覆盖）

> 📌 **作者指示**：下列三项（C4/C5/C6）在**项目导入后通过反编译源码查询确认**（原版 Minecraft / Forge / NeoForge 的对应类与方法签名），不需额外找示例仓库。以下候选 API 仅供查阅方向，**以反编译源码为准**。


- **C4 服务端聊天事件**（作答入口）：候选 `ServerChatEvent`（Forge：`net.minecraftforge.event.ServerChatEvent`；NeoForge：`net.neoforged.neoforge.event.ServerChatEvent`，均在 game/forge 总线）。需确认拦截/取消方式（`setCanceled` / 改写消息）与两版字段差异。
  > ⚠️ **`ServerChatEvent` 与本项目其它作答/生命周期事件都是游戏总线事件**。在 **NeoForge + Loom dev** 下，用 KLF `@EventBusSubscriber` 自动注册游戏总线订阅会触发跨模块层 `IllegalAccessError`（见 kotlinlangforge.md §3）。本项目把这些订阅集中在 `platform/PlatformEvents`：Forge 保留 `@EventBusSubscriber`（Stonecutter `//? if forge`），NeoForge 去注解、在 `Blackboard.init`（GAME 层）手动 `NeoForge.EVENT_BUS.register(PlatformEvents)`。详见 multiloader-build.md §9.4。
- **C5 按战利品表发奖**：`LootTable` + `LootParams.Builder`（`LootContextParams.ORIGIN`/`THIS_ENTITY`）→ `getRandomItems(...)`，再 `player.getInventory().add` 或掉落。1.20.1 与 1.21.x 的 `LootParams`/`LootContextParamSet` API 有差异，需各取一例。
- **C6 `Component` ↔ JSON/NBT**（持久化题面 `content`）：候选 1.20.1 `Component.Serializer.toJson(Component)` / `fromJson(String)`；1.21.x 需要 registries（`Component.Serializer.toJson(Component, HolderLookup.Provider)` 或 `ComponentSerialization.CODEC` + `RegistryOps`）。**与 §3 的 registries-provider 变化一致**，但精确 API 落地验证。
  - 备选实现：题面 `content` 不手动转 JSON，直接随 `getUpdateTag/saveAdditional` 的 `HolderLookup.Provider` 用 `ComponentSerialization.CODEC` 编码进 `CompoundTag`，避免字符串中转。

---

## 7. 映射到 Blackboard

| 平台边界（internal-core-api.md） | 本文件依据 |
| --- | --- |
| 方块/方块实体/物品注册 | §1（`Supplier<T>` 统一 + Stonecutter 隔离句柄）|
| `content` 同步到客户端渲染 | §2（`getUpdateTag` + `sendBlockUpdated`），无需自定义包（§4）|
| NBT 存取 registries 形参 | §3 |
| `PlatformComponents.serialize/deserialize` | §6 C6（待验证）|
| 作答聊天入口 | §6 C4（Forge 用 KLF `@EventBusSubscriber`；NeoForge dev 改手动注册游戏总线，见 kotlinlangforge.md §3）|
| 发奖 | §6 C5（待验证）|

落地包归属：注册 → `content/`；同步基类 → `content/`；平台差异 import/句柄 → 就地 `//? if forge`/`//? if neoforge`；`PlatformComponents` → `platform/`。

---

## 修订记录
- 2026-06-30：依据 FarmersDelight `1.20`（Forge）与 `1.21`（NeoForge）分支的 `ModBlocks`、`ModBlockEntityTypes`、`SyncedBlockEntity`、`ModNetworking` 归档注册/同步/网络差异。C4/C5/C6 标记为 FD 未覆盖、待验证。
- 2026-07-01：§6 C4 与 §7 映射表补——NeoForge + Loom dev 下游戏总线 `@EventBusSubscriber`（如 `ServerChatEvent`）会触 KLF `getGameBus` 跨模块层 `IllegalAccessError`，改为 `PlatformEvents` 在 `Blackboard.init` 手动注册游戏总线（Forge 仍用注解）。详见 kotlinlangforge.md §3、multiloader-build.md §9.4。
