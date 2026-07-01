package com.tonywww.blackboard.content

import com.tonywww.blackboard.api.board.BlackboardType
import com.tonywww.blackboard.api.board.RewardContext
import com.tonywww.blackboard.api.question.AnswerResult
import com.tonywww.blackboard.api.question.Question
import com.tonywww.blackboard.api.registry.BlackboardRegistries
import com.tonywww.blackboard.api.render.BlackboardRendering
import com.tonywww.blackboard.api.render.RenderContext
import com.tonywww.blackboard.core.questionFromNbt
import com.tonywww.blackboard.core.toClientNbt
import com.tonywww.blackboard.core.toNbt
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
//? if forge {
import net.minecraft.core.RegistryAccess
//?}

/**
 * Blackboard block entity: holds a single shared [Question] (§13(5): every player sees the same
 * one), persists it server-side, and syncs an answer-free copy to clients for rendering.
 *
 * `open` so other mods can subclass and override [onSolved]. The question/type are driven by the
 * server (BlackboardManager / answer handling, later milestones); this class owns storage, sync,
 * and the solved/destroy behaviour.
 *
 * Platform boundary: 1.21.1 threads a [HolderLookup.Provider] through `saveAdditional` /
 * `loadAdditional` / `getUpdateTag`; 1.20.1 has no such parameter and obtains one from the level.
 */
open class BlackboardBlockEntity(pos: BlockPos, state: BlockState) :
    SyncedBlockEntity(ModBlockEntities.BLACKBOARD.get(), pos, state) {

    /** Id of the [BlackboardType] governing this board's behaviour. */
    var blackboardTypeId: ResourceLocation? = null
        private set

    /** The currently posted question, or `null` if none has been generated yet. */
    var question: Question? = null
        private set

    /** Number of answer attempts made against the current question. */
    var attempts: Int = 0
        private set

    /** Short identifier used to address this board in chat answers (§13(1): scheme not finalized). */
    var boardId: String = ""
        private set

    /** Pinned question generator id (from the placed item's NBT); `null` = use the type's selection. */
    var generatorId: ResourceLocation? = null
        private set

    /** Resolves [blackboardTypeId] against the frozen registry, or `null` if absent/unknown. */
    val blackboardType: BlackboardType?
        get() = blackboardTypeId?.let { BlackboardRegistries.BLACKBOARD_TYPES.get(it) }

    /** Assigns a [boardId] on first placement if one is not already set. */
    fun assignBoardIdIfAbsent(random: RandomSource) {
        if (boardId.isEmpty()) {
            boardId = random.nextInt().toUInt().toString(16) // TODO(§13): finalize boardId scheme
            setChanged()
        }
    }

    /** Sets the governing blackboard type (server-side). */
    fun setBlackboardType(id: ResourceLocation?) {
        blackboardTypeId = id
        setChanged()
    }

    /** Posts [q] as the current question, resets attempts, and re-syncs to clients (server-side). */
    fun setQuestion(q: Question?) {
        question = q
        attempts = 0
        inventoryChanged()
    }

    /** Records a failed attempt and returns the new attempt count (server-side). */
    fun incrementAttempts(): Int {
        attempts++
        setChanged()
        return attempts
    }

    /**
     * Called when [player] answers correctly. Default behaviour (§13(3)): award via the board type,
     * then destroy the block. Subclasses may override to keep the block, regenerate, etc.
     */
    open fun onSolved(player: ServerPlayer, result: AnswerResult.Correct) {
        val serverLevel = level as? ServerLevel ?: return
        val q = question
        val type = blackboardType
        if (q != null && type != null) {
            type.onSolved(RewardContext(serverLevel, blockPos, blockState, player, q, result, type))
        }
        serverLevel.destroyBlock(blockPos, false)
    }

    override fun setRemoved() {
        super.setRemoved()
        // Client-side: tear down the in-world render for this board when it is removed (block broken /
        // chunk unloaded), so the panel does not linger after the blackboard is gone.
        val lvl = level
        if (lvl != null && lvl.isClientSide) BlackboardRendering.renderer.remove(blockPos)
    }

    private fun refreshClientRender() {
        val lvl = level ?: return
        if (!lvl.isClientSide) return
        val q = question ?: return
        BlackboardRendering.renderer.render(object : RenderContext {
            override val level: Level = lvl
            override val pos: BlockPos = blockPos
            override val boardId: String = this@BlackboardBlockEntity.boardId
            override val blockState: BlockState = this@BlackboardBlockEntity.blockState
            override val content: Component = q.content
        })
    }

    // ---- Persistence (full, with answer Data) & client sync (answer-free) ----

    //? if forge {
    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        writeSaveData(tag, registriesOrEmpty())
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        readSaveData(tag, registriesOrEmpty())
    }

    override fun getUpdateTag(): CompoundTag = CompoundTag().also { writeClientData(it, registriesOrEmpty()) }

    private fun registriesOrEmpty(): HolderLookup.Provider = level?.registryAccess() ?: RegistryAccess.EMPTY
    //?} else {
    /*override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        writeSaveData(tag, registries)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        readSaveData(tag, registries)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        CompoundTag().also { writeClientData(it, registries) }
    *///?}

    private fun writeSaveData(tag: CompoundTag, registries: HolderLookup.Provider) {
        blackboardTypeId?.let { tag.putString(KEY_TYPE, it.toString()) }
        generatorId?.let { tag.putString(KEY_GENERATOR, it.toString()) }
        if (boardId.isNotEmpty()) tag.putString(KEY_BOARD_ID, boardId)
        tag.putInt(KEY_ATTEMPTS, attempts)
        question?.let { tag.put(KEY_QUESTION, it.toNbt(registries)) }
    }

    private fun readSaveData(tag: CompoundTag, registries: HolderLookup.Provider) {
        blackboardTypeId =
            if (tag.contains(KEY_TYPE)) ResourceLocation.tryParse(tag.getString(KEY_TYPE)) else null
        generatorId =
            if (tag.contains(KEY_GENERATOR)) ResourceLocation.tryParse(tag.getString(KEY_GENERATOR)) else null
        boardId = tag.getString(KEY_BOARD_ID)
        attempts = tag.getInt(KEY_ATTEMPTS)
        question =
            if (tag.contains(KEY_QUESTION)) questionFromNbt(tag.getCompound(KEY_QUESTION), registries) else null
        refreshClientRender()
    }

    /** Client-sync payload: only `Generator` + `Content` (never the answer `Data`). */
    private fun writeClientData(tag: CompoundTag, registries: HolderLookup.Provider) {
        blackboardTypeId?.let { tag.putString(KEY_TYPE, it.toString()) }
        if (boardId.isNotEmpty()) tag.putString(KEY_BOARD_ID, boardId)
        question?.let { tag.put(KEY_QUESTION, it.toClientNbt(registries)) }
    }

    companion object {
        private const val KEY_TYPE = "Type"
        private const val KEY_GENERATOR = "Generator"
        private const val KEY_BOARD_ID = "BoardId"
        private const val KEY_ATTEMPTS = "Attempts"
        private const val KEY_QUESTION = "Question"
    }
}
