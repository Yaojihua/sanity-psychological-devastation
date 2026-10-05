package piloser.sanitypd.item;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.enchantment.PsychicDeprivationEnchantment;
import piloser.sanitypd.enchantment.SanityEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
/**
 * Creative mode tab for this mod.
 *
 * <p>It exists for convenience and holds the garland (the only item inherited from upstream, which
 * had no tab at all), Psychic Deprivation IV (the highest level reachable from natural sources,
 * which cap at 4), Psychic Deprivation 255 (the anvil limit, for trying the maxed-out effect
 * directly), Psychic Drain I (the other enchantment, which has no level scaling) and Psychic
 * Protection IV (the armor enchantment, whose natural cap is also 4).
 *
 * <p>Adding an enchanted book here also requires matching lang entries
 * ({@code enchantment.<modid>.<id>} in English and Chinese), otherwise the book shows the raw
 * translation key.
 *
 * <p>{@code Registries.CREATIVE_MODE_TAB} is a vanilla built-in registry (part of
 * {@code BuiltInRegistries}), so Forge fires {@code RegisterEvent} for it and
 * {@code DeferredRegister} works here. This is unlike {@code damage_type}, which is a pure datapack
 * registry.
 */
public final class CreativeTabRegistry
{
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SanityMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + SanityMod.MODID))
            .icon(() -> new ItemStack(ItemRegistry.GARLAND.get()))
            .displayItems((params, output) ->
            {
                output.accept(ItemRegistry.GARLAND.get());
                output.accept(ItemRegistry.INNER_SHARD.get());
                output.accept(ItemRegistry.INNER_CLUMP.get());
                output.accept(ItemRegistry.INNER_CORE.get());
                output.accept(ItemRegistry.SHADOW_SWORD.get());
                output.accept(ItemRegistry.SHADOW_AXE.get());
                // Mind stabilizer trio
                output.accept(ItemRegistry.STABILIZER_A.get());
                output.accept(ItemRegistry.STABILIZER_B.get());
                output.accept(ItemRegistry.STABILIZER_C.get());
                // Delta was registered at the end of ItemRegistry to keep the ids stable; the tab keeps the
                // family in one place, which is where a player looks for it.
                output.accept(ItemRegistry.STABILIZER_D.get());
                // Macaron: one 3x3 recipe differs only in the dye, and all eight share one cooldown
                for (RegistryObject<Item> macaron : ItemRegistry.MACARONS())
                    output.accept(macaron.get());
                // Thought chain: the controller, then the betas grouped by row - worldviews
                // first, then methodologies, so the tab reads like the screen does.
                output.accept(ItemRegistry.PSYCHIC_CONTROLLER.get());
                output.accept(ItemRegistry.THOUGHT_LAW_OF_THE_JUNGLE.get());
                output.accept(ItemRegistry.THOUGHT_NATURE_AFFINITY.get());
                output.accept(ItemRegistry.THOUGHT_LUCID_ELATION.get());
                output.accept(ItemRegistry.THOUGHT_SOCIALIZATION.get());
                output.accept(ItemRegistry.THOUGHT_DUPLICITY.get());
                // Madness batch. ⚠️ A thought that is not listed here is invisible in the creative
                // tab AND in JEI, because JEI builds its item list from the tab contents (this mod ships no
                // JEI plugin). An earlier build shipped without these five lines, and the owner reported exactly that
                // ("I cannot see a single new thought"). Registering an item is not the same as listing it.
                // Order follows the composure group above: two worldviews, then three methodologies.
                output.accept(ItemRegistry.THOUGHT_IDENTIFICATION_WITH_THE_AGGRESSOR.get());
                output.accept(ItemRegistry.THOUGHT_FIGHT_OR_FLIGHT.get());
                output.accept(ItemRegistry.THOUGHT_CATHARSIS.get());
                output.accept(ItemRegistry.THOUGHT_COMMAND_HALLUCINATION.get());
                output.accept(ItemRegistry.THOUGHT_PSYCHOMOTOR_AGITATION.get());
                // Sleep debt (worldview) and instrumental aggression (methodology).
                output.accept(ItemRegistry.THOUGHT_SLEEP_DEBT.get());
                output.accept(ItemRegistry.THOUGHT_INSTRUMENTAL_AGGRESSION.get());
                // Stress-induced analgesia (methodology/endurance), irritability
                // (methodology/madness) and conversion disorder (worldview/servitude - the first servitude
                // thought in the mod). Appended here as well as in ItemRegistry, for the same reason.
                output.accept(ItemRegistry.THOUGHT_STRESS_INDUCED_ANALGESIA.get());
                output.accept(ItemRegistry.THOUGHT_IRRITABILITY.get());
                output.accept(ItemRegistry.THOUGHT_CONVERSION_DISORDER.get());
                // The first chaos-restraint thought: appended here as well as in ItemRegistry, so its
                // registry ids do not shift for anyone else.
                output.accept(ItemRegistry.THOUGHT_DEPERSONALIZATION.get());
                // Lost memory fragment (story item, part 1): registered at the end of ItemRegistry to keep
                // the ids stable, and listed here because an unlisted item is invisible in the tab AND in
                // JEI (see the warning above).
                output.accept(ItemRegistry.LOST_MEMORY_FRAGMENT.get());
                // The second memory fragment (the Nether one). Same reason as above for listing it here.
                output.accept(ItemRegistry.LOST_MEMORY_FRAGMENT_NETHER.get());
                // The third memory fragment (the End one), listed for the same reason.
                output.accept(ItemRegistry.LOST_MEMORY_FRAGMENT_END.get());
                // The final memory fragment, listed for the same reason.
                output.accept(ItemRegistry.LOST_MEMORY_FRAGMENT_FINALE.get());
                // Inner mob spawn egg trio
                output.accept(ItemRegistry.ROTTING_STALKER_SPAWN_EGG.get());
                output.accept(ItemRegistry.SNEAKING_TERROR_SPAWN_EGG.get());
                output.accept(ItemRegistry.SCREAMING_CRAWLER_SPAWN_EGG.get());
                // Highest level obtainable in survival
                output.accept(book(SanityEnchantments.PSYCHIC_DEPRIVATION, PsychicDeprivationEnchantment.NATURAL_MAX_LEVEL));
                // Anvil limit, for playing with a maxed-out book right away
                output.accept(book(SanityEnchantments.PSYCHIC_DEPRIVATION, PsychicDeprivationEnchantment.MAX_LEVEL));
                output.accept(book(SanityEnchantments.PSYCHIC_DRAIN, 1));
                // Psychic Protection: armor enchantment, natural cap 4. Read the enchantment's own
                // limit instead of hardcoding the number.
                output.accept(book(SanityEnchantments.PSYCHIC_PROTECTION,
                        SanityEnchantments.PSYCHIC_PROTECTION.get().getMaxLevel()));
            })
            .build());

    private CreativeTabRegistry() {}

    private static ItemStack book(RegistryObject<Enchantment> enchantment, int level)
    {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(stack, new EnchantmentInstance(enchantment.get(), level));
        return stack;
    }

    public static void register(IEventBus eventBus)
    {
        TABS.register(eventBus);
    }
}
