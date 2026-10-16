package piloser.sanitypd.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import piloser.sanitypd.SanityMod;

/**
 * The blocks of this mod.
 *
 * <p>{@code ForgeRegistries.BLOCKS} is a real Forge registry, so {@code DeferredRegister} works here - it
 * is not one of the datapack-only registries such as {@code damage_type}, which must never be registered
 * this way.
 *
 * <p>Registration order matters for the same reason it does in
 * {@link piloser.sanitypd.item.ItemRegistry}: new blocks are appended, never inserted, so the registry
 * ids of everything already sitting in a saved world stay where they are.
 */
public final class BlockRegistry
{
    public static final DeferredRegister<Block> DEFERRED_REGISTER =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SanityMod.MODID);

    /**
     * Block entities of this mod.
     *
     * <p>Registered next to the block rather than in a registry of its own, so the pairing that matters - a
     * block entity type must name the blocks it belongs to - is written in one file. Appended like every other
     * registry entry: the type id is stored in saved chunks, so inserting one would move the existing ids.
     */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SanityMod.MODID);

    /**
     * The thought transcription table: this mod's first block, made from an enchanting table.
     *
     * <p>Every property below is the enchanting table's own chain, read off {@code Blocks.<clinit>} in the
     * mapped jar rather than copied from memory: map colour red, the bass-drum note-block instrument,
     * requires the correct tool for drops, light level 7, and strength 5.0 / 1200.0. The sound type is
     * deliberately not set - the enchanting table does not set it either, so both keep the default
     * {@code SoundType.STONE}.
     *
     * <p>Requiring the correct tool is only half of that rule: the block must also be listed in a mineable
     * tag ({@code data/minecraft/tags/blocks/mineable/pickaxe.json}), otherwise no tool ever counts as
     * correct and it would never drop. {@code audit-bindings.mjs} checks the pairing, because getting it
     * wrong is silent - the block places and mines, it just leaves nothing behind.
     */
    public static final RegistryObject<Block> THOUGHT_TRANSCRIPTION_TABLE = DEFERRED_REGISTER.register(
            "thought_transcription_table",
            () -> new ThoughtTranscriptionTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 7)
                    .strength(5.0F, 1200.0F)));

    /**
     * Shadow TNT: vanilla TNT whose blast deals psychic damage.
     *
     * <p>The properties are copied from {@code Blocks.TNT} with {@code Properties.copy(...)} rather than
     * re-typed, so hardness/resistance (both instant-break), the grass sound, the map colour, the lava
     * ignition rule and the "never a redstone conductor" predicate all stay vanilla by construction. The
     * vanilla chain was read out of {@code Blocks.<clinit>} to confirm what that copy contains:
     * {@code of() -> mapColor(MapColor.FIRE) -> instabreak() -> sound(SoundType.GRASS) -> ignitedByLava()
     * -> isRedstoneConductor(never)}.
     *
     * <p>Behaviour lives in {@link ShadowTntBlock}; see that class for the four ignition entry points.
     */
    public static final RegistryObject<Block> SHADOW_TNT = DEFERRED_REGISTER.register(
            "shadow_tnt",
            () -> new ShadowTntBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.TNT)));

    /**
     * The thought transcription table's block entity type: six slots, see
     * {@link ThoughtTranscriptionTableBlockEntity}.
     *
     * <p>{@code build(null)} - the argument names a "type" that only matters for a data-fixer, and passing
     * {@code null} is what vanilla does for both of its plain container block entities.
     */
    public static final RegistryObject<BlockEntityType<ThoughtTranscriptionTableBlockEntity>> THOUGHT_TRANSCRIPTION_TABLE_ENTITY =
            BLOCK_ENTITIES.register("thought_transcription_table",
                    () -> BlockEntityType.Builder.of(ThoughtTranscriptionTableBlockEntity::new,
                            THOUGHT_TRANSCRIPTION_TABLE.get()).build(null));

    public static void register(IEventBus eventBus)
    {
        DEFERRED_REGISTER.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }

    private BlockRegistry() {}
}
