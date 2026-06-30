package com.tonywww.blackboard.content

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
//? if forge {
import net.minecraft.network.Connection
//?} else {
/*import net.minecraft.core.HolderLookup
*///?}

/**
 * Block-entity base that mirrors its saved data to the client through the vanilla update-tag
 * channel, so no custom network packet is required for blackboard rendering.
 *
 * Subclasses call [inventoryChanged] after mutating synced state to push a block update. Mirrors
 * FarmersDelight's `SyncedBlockEntity`; the only version difference is the `getUpdateTag` signature
 * (which gains a `HolderLookup.Provider` on 1.21.1) and the now-unnecessary `onDataPacket` override.
 */
abstract class SyncedBlockEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState) :
    BlockEntity(type, pos, state) {

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket? =
        ClientboundBlockEntityDataPacket.create(this)

    //? if forge {
    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun onDataPacket(connection: Connection, packet: ClientboundBlockEntityDataPacket) {
        packet.tag?.let { load(it) }
    }
    //?} else {
    /*override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)
    *///?}

    /** Marks the BE dirty and pushes a block update so the client re-syncs (and re-renders). */
    protected fun inventoryChanged() {
        setChanged()
        val lvl = level ?: return
        lvl.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
    }
}
