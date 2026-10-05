package piloser.sanitypd.item;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.entity.EntityRegistry;
import piloser.sanitypd.thought.ThoughtCategory;
import piloser.sanitypd.thought.PsychicControllerItem;
import piloser.sanitypd.thought.ThoughtItem;
import piloser.sanitypd.thought.ThoughtType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistry
{
    public static final DeferredRegister<Item> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, SanityMod.MODID);

    public static final RegistryObject<Item> GARLAND = DEFERRED_REGISTER.register("garland", GarlandItem::new);

    // ---------------------------------------------------------------- inner materials
    // Drop sources are in piloser.sanitypd.loot.InnerLoot: Inner Shard drops only when a player with
    // madness >= 0.7 kills a chicken, cow, pig or sheep (1 to 3), and Inner Clump drops from inner
    // mobs (1 to 2). Recipes: 4 shards craft a clump, 4 glass + 1 clump + 2 amethyst shards +
    // 3 redstone craft an inner core.

    /** Inner Shard. */
    public static final RegistryObject<Item> INNER_SHARD = DEFERRED_REGISTER.register("inner_shard",
            () -> new Item(new Item.Properties()));

    /** Inner Clump. */
    public static final RegistryObject<Item> INNER_CLUMP = DEFERRED_REGISTER.register("inner_clump",
            () -> new Item(new Item.Properties()));

    /** Inner Core. */
    public static final RegistryObject<Item> INNER_CORE = DEFERRED_REGISTER.register("inner_core",
            () -> new Item(new Item.Properties()));

    /**
     * Shadow Sword: 8 attack damage, 8 extra psychic damage on hit, 1500 durability, cannot take
     * Mending. Holding right click for 4 seconds spends sanity to repair it (see
     * {@link ShadowSwordItem}).
     */
    public static final RegistryObject<Item> SHADOW_SWORD = DEFERRED_REGISTER.register("shadow_sword", ShadowSwordItem::new);

    /**
     * Shadow Axe: same mechanics as the Shadow Sword (both share {@link ShadowCharge}), with 10
     * attack damage, 10 extra psychic damage on hit, 1500 durability and no Mending.
     */
    public static final RegistryObject<Item> SHADOW_AXE = DEFERRED_REGISTER.register("shadow_axe", ShadowAxeItem::new);

    // ---------------------------------------------------------------- mind stabilizers
    // All three are food (they restore no hunger or saturation, so they can be eaten while full)
    // and each has its own cooldown.
    // Recipes: data/sanitypd/recipes/stabilizer_{a,b,c}.json, one shared 3x3 layout that differs
    // only in the center flower and yields 4 items.

    /** Mind Stabilizer A (formerly "Alpha"): restores 35 sanity instantly; 15 s cooldown; center is a cornflower. */
    public static final RegistryObject<Item> STABILIZER_A = DEFERRED_REGISTER.register("stabilizer_a",
            () -> new StabilizerItem(StabilizerItem.Kind.ALPHA,
                    new Item.Properties().food(StabilizerItem.stabilizerFood())));

    /** Mind Stabilizer B (formerly "Beta"): 3 minutes of mania immunity; 5 min cooldown; center is a poppy. */
    public static final RegistryObject<Item> STABILIZER_B = DEFERRED_REGISTER.register("stabilizer_b",
            () -> new StabilizerItem(StabilizerItem.Kind.BETA,
                    new Item.Properties().food(StabilizerItem.stabilizerFood())));

    /** Mind Stabilizer C (formerly "Gamma"): 1 minute of inner immunity; 2 min cooldown; center is a lilac. */
    public static final RegistryObject<Item> STABILIZER_C = DEFERRED_REGISTER.register("stabilizer_c",
            () -> new StabilizerItem(StabilizerItem.Kind.GAMMA,
                    new Item.Properties().food(StabilizerItem.stabilizerFood())));

    // ---------------------------------------------------------------- macarons
    // Eight colours of the same cake. They all share one food value and one set of effects, and they
    // deliberately share one cooldown: eating any colour puts all eight on cooldown for 5 seconds
    // (see MacaronItem#startSharedCooldown), so the colours cannot be chained.
    // Recipes: data/sanitypd/recipes/macaron_<colour>.json, one 3x3 layout differing only in the dye.
    // The milk bucket in the layout is a container ingredient, so crafting returns an empty bucket the
    // same way the vanilla cake recipe does.
    //
    // WARNING: this list must be declared BEFORE the eight constants below. Java runs static
    // initializers in source order, and the constants fill this list through macaron(); declaring the
    // list after them leaves it null while they run, which fails the whole mod at construction time
    // with an ExceptionInInitializerError. Adding a colour means adding: an enum constant in
    // MacaronItem.Colour, a constant below, a texture, a model, a recipe and two language entries per
    // language.
    private static final java.util.List<RegistryObject<Item>> MACARONS = new java.util.ArrayList<>();

    /** Red macaron. */
    public static final RegistryObject<Item> MACARON_RED = macaron(MacaronItem.Colour.RED, "macaron_red");
    /** Orange macaron. */
    public static final RegistryObject<Item> MACARON_ORANGE = macaron(MacaronItem.Colour.ORANGE, "macaron_orange");
    /** Yellow macaron. */
    public static final RegistryObject<Item> MACARON_YELLOW = macaron(MacaronItem.Colour.YELLOW, "macaron_yellow");
    /** Green macaron (crafted with lime dye, like the vanilla "lime" colour name). */
    public static final RegistryObject<Item> MACARON_GREEN = macaron(MacaronItem.Colour.GREEN, "macaron_green");
    /** Light blue macaron (the art is cyan; the dye and the name are both light blue). */
    public static final RegistryObject<Item> MACARON_LIGHT_BLUE = macaron(MacaronItem.Colour.LIGHT_BLUE, "macaron_light_blue");
    /** Blue macaron. */
    public static final RegistryObject<Item> MACARON_BLUE = macaron(MacaronItem.Colour.BLUE, "macaron_blue");
    /** Purple macaron. */
    public static final RegistryObject<Item> MACARON_PURPLE = macaron(MacaronItem.Colour.PURPLE, "macaron_purple");
    /** Pink macaron. */
    public static final RegistryObject<Item> MACARON_PINK = macaron(MacaronItem.Colour.PINK, "macaron_pink");

    /**
     * Every macaron, in creative-tab order.
     *
     * <p>The single source of truth for "which colours exist": the shared cooldown loop, the creative
     * tab and the self-check all read it, and {@link #macaron} is the only thing that fills it. Callers
     * must not modify it.
     *
     * @return an unmodifiable view of the eight macarons
     */
    public static java.util.List<RegistryObject<Item>> MACARONS()
    {
        return java.util.Collections.unmodifiableList(MACARONS);
    }

    /**
     * Registers one macaron colour and adds it to {@link #MACARONS}.
     *
     * <p>The id is a plain literal at every call site rather than {@code "macaron_" + colour.id()}: the
     * static binding audit reads these registrations with a regular expression, and a built-up id shows
     * up there as the meaningless intermediate {@code macaron_}.
     */
    private static RegistryObject<Item> macaron(MacaronItem.Colour colour, String id)
    {
        RegistryObject<Item> item = DEFERRED_REGISTER.register(id,
                () -> new MacaronItem(colour, new Item.Properties().food(MacaronItem.macaronFood())));
        MACARONS.add(item);
        return item;
    }

    // ---------------------------------------------------------------- inner mob spawn eggs
    // The egg textures are 16x16 solid egg shapes used as-is at their original resolution, so each
    // item draws its own texture with a white tint instead of using the vanilla grayscale
    // template_spawn_egg:
    //   - model parent = item/generated with a single layer0 = sanitypd:item/<id>
    //   - both tint colors are 0xFFFFFF, and white is the identity for multiplication, so the drawn
    //     result matches the source texture exactly whether or not Forge's color handler registers
    //     the egg as an ItemColor
    //   - dispenser spawning behavior comes for free from Forge
    // ForgeSpawnEggItem (which takes a Supplier) is required instead of vanilla SpawnEggItem (which
    // takes an EntityType): the vanilla class resolves getType(null) from the item's static
    // initializer, which hits the DeferredRegister "registry not present" ordering problem and
    // crashes.

    /** Rotting Stalker spawn egg. */
    public static final RegistryObject<Item> ROTTING_STALKER_SPAWN_EGG = DEFERRED_REGISTER.register("rotting_stalker_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityRegistry.ROTTING_STALKER, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));

    /** Sneaking Terror spawn egg. */
    public static final RegistryObject<Item> SNEAKING_TERROR_SPAWN_EGG = DEFERRED_REGISTER.register("sneaking_terror_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityRegistry.SNEAKING_TERROR, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));

    /** Screaming Crawler spawn egg. */
    public static final RegistryObject<Item> SCREAMING_CRAWLER_SPAWN_EGG = DEFERRED_REGISTER.register("screaming_crawler_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityRegistry.SCREAMING_CRAWLER, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));

    // ---------------------------------------------------------------- thought chain
    // The closed beta set: the controller plus five composure thoughts. Four of them are tiered and
    // "Duplicity" is not, so both tooltip branches are reachable on a real client.
    // Every id below is a plain literal: the static binding audit reads these registrations with a
    // regular expression, and a built-up id would show up there as a meaningless intermediate.
    // Recipes: data/sanitypd/recipes/thought_*.json, plus one shapeless recipe for Nature Affinity.
    // Thoughts are stacksTo(1) because a thought is equipment rather than a resource.

    /** Psychic Controller: right-click (either hand, on nothing) to open your own thought chain. */
    public static final RegistryObject<Item> PSYCHIC_CONTROLLER = DEFERRED_REGISTER.register("psychic_controller",
            () -> new PsychicControllerItem(new Item.Properties().stacksTo(1)));

    /** Worldview: Law of the Jungle - composure, tiered. */
    public static final RegistryObject<Item> THOUGHT_LAW_OF_THE_JUNGLE = DEFERRED_REGISTER.register(
            "thought_law_of_the_jungle",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.COMPOSURE), true));

    /** Worldview: Nature Affinity - composure, tiered. */
    public static final RegistryObject<Item> THOUGHT_NATURE_AFFINITY = DEFERRED_REGISTER.register(
            "thought_nature_affinity",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.COMPOSURE), true));

    /** Worldview: Lucid Elation - composure, tiered. */
    public static final RegistryObject<Item> THOUGHT_LUCID_ELATION = DEFERRED_REGISTER.register(
            "thought_lucid_elation",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.COMPOSURE), true));

    /** Methodology: Socialization - composure, tiered. */
    public static final RegistryObject<Item> THOUGHT_SOCIALIZATION = DEFERRED_REGISTER.register(
            "thought_socialization",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.COMPOSURE), true));

    /**
     * Methodology: Duplicity - composure and <b>not tiered</b>.
     *
     * <p>An untiered thought has no tier ladder, so it shows no "hold shift" hint and no detail page at
     * all; its current effect never changes. It exists in the beta set precisely so that branch can be
     * exercised on a real client.
     */
    public static final RegistryObject<Item> THOUGHT_DUPLICITY = DEFERRED_REGISTER.register(
            "thought_duplicity",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.COMPOSURE), false));

    // ---------------------------------------------------------------- madness thoughts
    // The second batch: five madness-typed thoughts, which together turn on the madness mindset
    // (Mindsets.MADNESS). Three are tiered and two are not, so both tooltip branches stay reachable.
    // These registrations are appended after every existing one on purpose: inserting an entry
    // in the middle shifts every later registry id, which the game then has to remap.

    /**
     * Methodology: Catharsis - madness and <b>not tiered</b>.
     *
     * <p>While sanity is below 40%, a hit also applies the mod's psychic drain to the target.
     */
    public static final RegistryObject<Item> THOUGHT_CATHARSIS = DEFERRED_REGISTER.register(
            "thought_catharsis",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.MADNESS), false));

    /** Methodology: Command Hallucination - madness, tiered: attack rises while an inner line is shown. */
    public static final RegistryObject<Item> THOUGHT_COMMAND_HALLUCINATION = DEFERRED_REGISTER.register(
            "thought_command_hallucination",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.MADNESS), true));

    /** Worldview: Identification with the Aggressor - madness, tiered: hit back harder for six seconds. */
    public static final RegistryObject<Item> THOUGHT_IDENTIFICATION_WITH_THE_AGGRESSOR = DEFERRED_REGISTER.register(
            "thought_identification_with_the_aggressor",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.MADNESS), true));

    /**
     * Worldview: Fight or Flight - madness and <b>not tiered</b>.
     *
     * <p>Two mutually exclusive states below 45% sanity: attack up and movement down while untouched,
     * the reverse for five seconds after being hurt.
     */
    public static final RegistryObject<Item> THOUGHT_FIGHT_OR_FLIGHT = DEFERRED_REGISTER.register(
            "thought_fight_or_flight",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.MADNESS), false));

    /** Methodology: Psychomotor Agitation - madness, tiered: movement rises at very low sanity or in mania. */
    public static final RegistryObject<Item> THOUGHT_PSYCHOMOTOR_AGITATION = DEFERRED_REGISTER.register(
            "thought_psychomotor_agitation",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.MADNESS), true));

    // ---------------------------------------------------------------- sleep debt and instrumental aggression
    // Two more thoughts, appended at the end again (never insert in the middle of the registry).

    /**
     * Worldview: Sleep Debt - composure <b>and</b> endurance, untiered.
     *
     * <p>The first thought on two axes at once, which the chain already supports: {@code types()} is a list,
     * the tag decides the row, and a tier is taken from the highest of the types. Its two effects are both
     * existing mechanics - the sanity a sleep restores is cut, and the vanilla "monsters prevent rest" check
     * is skipped - so nothing new is introduced beyond one mixin on that single check.
     */
    public static final RegistryObject<Item> THOUGHT_SLEEP_DEBT = DEFERRED_REGISTER.register(
            "thought_sleep_debt",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.COMPOSURE, ThoughtType.ENDURANCE), false));

    /**
     * Methodology: Instrumental Aggression - madness, untiered.
     *
     * <p>Attack damage up, and the "feeding" sanity reward for hitting a weakened monster is switched off
     * entirely: the aggression is instrumental, so it pays in damage rather than in sanity.
     */
    public static final RegistryObject<Item> THOUGHT_INSTRUMENTAL_AGGRESSION = DEFERRED_REGISTER.register(
            "thought_instrumental_aggression",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.MADNESS), false));

    // ---------------------------------------------------------------- stabilizer delta
    /**
     * Mood Stabilizer δ: <b>takes</b> 40 sanity away instantly, 5 s cooldown; center is a wither rose.
     *
     * <p>⚠️ Registered <b>here, at the end</b>, and not next to its three siblings further up: an item
     * inserted in the middle of this class shifts every later registry id, so the game has to remap them
     * all. The family is kept together by the comment and by the creative tab instead.
     */
    public static final RegistryObject<Item> STABILIZER_D = DEFERRED_REGISTER.register("stabilizer_d",
            () -> new StabilizerItem(StabilizerItem.Kind.DELTA,
                    new Item.Properties().food(StabilizerItem.stabilizerFood())));

    // ---------------------------------------------------------------- three new thoughts
    // Appended at the very end AGAIN: an entry inserted in the middle of this class shifts the
    // registry ids of every item declared after it, which the game has to remap for existing saves.

    /**
     * Methodology: Stress-Induced Analgesia - endurance and <b>not tiered</b>.
     *
     * <p>Below 60% sanity, being hurt restores 1 health, at most once every 0.3 s (6 ticks). The cooldown is
     * not decoration: this mod's psychic, true and mania damage all bypass the vanilla invulnerability
     * frames (see the damage-type tags), so without a rate limit a per-tick psychic source would turn the
     * heal into immortality.
     */
    public static final RegistryObject<Item> THOUGHT_STRESS_INDUCED_ANALGESIA = DEFERRED_REGISTER.register(
            "thought_stress_induced_analgesia",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.ENDURANCE), false));

    /**
     * Methodology: Irritability - madness, tiered: the mania effect's own attack bonus is raised.
     *
     * <p>Only pays out while the mania effect is on the player: with no mania there is nothing to amplify.
     * The bonus is a second {@code MULTIPLY_TOTAL} modifier, which vanilla sums with mania's own, so the two
     * add instead of multiplying (owner's wording, 2026-10-04).
     */
    public static final RegistryObject<Item> THOUGHT_IRRITABILITY = DEFERRED_REGISTER.register(
            "thought_irritability",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.MADNESS), true));

    /**
     * Worldview: Conversion Disorder - servitude, tiered: more of the player's psychic overflow becomes true
     * damage, and the same ratio raises what an inner entity takes.
     *
     * <p>The <b>first servitude-typed thought</b> in the mod (that axis had none), so the servitude mindset
     * needs four more before it can ever switch on - registered here without any change to the type itself.
     */
    public static final RegistryObject<Item> THOUGHT_CONVERSION_DISORDER = DEFERRED_REGISTER.register(
            "thought_conversion_disorder",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.SERVITUDE), true));

    /**
     * Worldview: Depersonalization - the first thought on the <b>chaos-restraint</b> axis, and the first
     * <b>tiered</b> thought that sits on two axes (chaos restraint + madness).
     *
     * <p>The owner's spec (2026-10-04): <b>purely negative</b>, but a necessary item for moving the story
     * along. Its effect is a <b>recovery ceiling</b> on sanity: with 1 / 3 / 5 chaos-restraint thoughts in the
     * chain the player can only recover to 80% / 70% / 60% of the maximum. The maximum itself is deliberately
     * untouched - see {@code ThoughtEffects.DEPERSONALIZATION} and {@code recoveryCeilingFraction}.
     *
     * <p>Its tier counts <b>chaos-restraint thoughts only</b>, not the higher of its two axes: the owner's rule
     * is "scaled by the number of chaos-restraint thoughts", so five madness thoughts must not deepen it
     * (see {@code ThoughtEffects#decidingType}).
     */
    public static final RegistryObject<Item> THOUGHT_DEPERSONALIZATION = DEFERRED_REGISTER.register(
            "thought_depersonalization",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.CHAOS_RESTRAINT, ThoughtType.MADNESS), true));

    // ---------------------------------------------------------------- lost memory fragment (story, part 1)
    // A story item: no gameplay effect, only the client-side presentation (hold right click -> HUD hidden
    // -> charge clip after 1 s -> black tape screen). Appended here at the very end again: an entry
    // inserted in the middle of this class shifts the registry ids of every item declared after it.
    // No recipe yet: the owner has not given one, and it is a story item rather than something to craft.

    /**
     * Lost Memory Fragment: hold right click to charge; the sequence is client-side (see
     * {@code client.MemorySequence}). Its id names its two script files
     * ({@code assets/sanitypd/memory/<id>_<language>.txt}), its watch marker and its language keys.
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT = DEFERRED_REGISTER.register(
            "lost_memory_fragment", () -> new LostMemoryFragmentItem("lost_memory_fragment"));

    /**
     * Lost Memory Fragment: Nether - the second fragment. Same item class, same sequence: only its id,
     * its script and its own "first viewing cannot be skipped" marker differ.
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_NETHER = DEFERRED_REGISTER.register(
            "lost_memory_fragment_nether", () -> new LostMemoryFragmentItem("lost_memory_fragment_nether"));

    /**
     * Lost Memory Fragment: End - the third fragment (the End, the dragon and the two reciters). Same item
     * class and the same sequence once more: only its id, its script and its own marker differ.
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_END = DEFERRED_REGISTER.register(
            "lost_memory_fragment_end", () -> new LostMemoryFragmentItem("lost_memory_fragment_end"));

    /**
     * Lost Memory Fragment: Finale - the last fragment. Same item class and sequence again; its script's
     * final line carries the per-line dark-red marker, which is a script concern rather than a code one.
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_FINALE = DEFERRED_REGISTER.register(
            "lost_memory_fragment_finale", () -> new LostMemoryFragmentItem("lost_memory_fragment_finale"));

    public static void register(IEventBus eventBus)
    {
        DEFERRED_REGISTER.register(eventBus);
    }
}