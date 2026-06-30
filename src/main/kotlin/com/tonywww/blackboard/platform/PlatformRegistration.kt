package com.tonywww.blackboard.platform

import com.tonywww.blackboard.api.BlackboardApi
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
//? if forge {
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
//?} else {
/*import net.minecraft.core.registries.Registries
import net.neoforged.neoforge.registries.DeferredRegister
*///?}

/**
 * Loader-agnostic factories for [DeferredRegister]s bound to the Blackboard namespace.
 *
 * Only the registry handle (and the `DeferredRegister` import) differs between Forge 1.20.1 and
 * NeoForge 1.21.1. Callers (e.g. `ModBlocks`) use the returned register uniformly and store the
 * resulting holders as `java.util.function.Supplier<T>` (both `RegistryObject` and NeoForge's
 * `DeferredHolder` implement `Supplier`).
 */
object PlatformRegistration {
    /** Create a block [DeferredRegister]. */
    fun blocks(): DeferredRegister<Block> = DeferredRegister.create(
        //? if forge {
        ForgeRegistries.BLOCKS,
        //?} else {
        /*Registries.BLOCK,
        *///?}
        BlackboardApi.MOD_ID,
    )

    /** Create an item [DeferredRegister]. */
    fun items(): DeferredRegister<Item> = DeferredRegister.create(
        //? if forge {
        ForgeRegistries.ITEMS,
        //?} else {
        /*Registries.ITEM,
        *///?}
        BlackboardApi.MOD_ID,
    )

    /** Create a block-entity-type [DeferredRegister]. */
    fun blockEntities(): DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(
        //? if forge {
        ForgeRegistries.BLOCK_ENTITY_TYPES,
        //?} else {
        /*Registries.BLOCK_ENTITY_TYPE,
        *///?}
        BlackboardApi.MOD_ID,
    )
}
