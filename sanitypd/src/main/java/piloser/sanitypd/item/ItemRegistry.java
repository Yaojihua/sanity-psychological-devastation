package piloser.sanitypd.item;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.block.BlockRegistry;
import piloser.sanitypd.block.ThoughtTranscriptionTableItem;
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
 * add instead of multiplying (the required wording).
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
 * <p>Specification: <b>purely negative</b>, but a necessary item for moving the story
     * along. Its effect is a <b>recovery ceiling</b> on sanity: with 1 / 3 / 5 chaos-restraint thoughts in the
     * chain the player can only recover to 80% / 70% / 60% of the maximum. The maximum itself is deliberately
     * untouched - see {@code ThoughtEffects.DEPERSONALIZATION} and {@code recoveryCeilingFraction}.
     *
 * <p>Its tier counts <b>chaos-restraint thoughts only</b>, not the higher of its two axes: the rule
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
 // No recipe yet: the author has not given one, and it is a story item rather than something to craft.

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

    /**
     * The four fragments in the order the story has to be watched in: Doubt, Longing, Bewilderment, The Name.
     *
     * <p>Declared here, next to the registrations, so the order lives with the ids instead of being spelled
     * out a second time somewhere else - the four story names are the language files' business, the four ids
     * are this list's, and the ordering rule reads this list rather than repeating it.
     *
     * <p>Read from the registry objects lazily (a {@code RegistryObject} has no value during class init), so
     * this is a list of holders, never of items.
     */
    public static final java.util.List<RegistryObject<Item>> MEMORY_FRAGMENT_ORDER = java.util.List.of(
            LOST_MEMORY_FRAGMENT,
            LOST_MEMORY_FRAGMENT_NETHER,
            LOST_MEMORY_FRAGMENT_END,
            LOST_MEMORY_FRAGMENT_FINALE);

    /**
     * The script key of the fragment that must be watched <b>through</b> before {@code scriptKey} may be
     * watched, or {@code null} when {@code scriptKey} is the first fragment or not one of them.
     *
     * <p>Used by the client's tape gate; returns ids, not items, because the client keys everything - scripts,
     * watch markers, language keys - off the fragment's own id string.
     */
    public static String previousFragmentScriptKey(String scriptKey)
    {
        for (int i = 1; i < MEMORY_FRAGMENT_ORDER.size(); i++)
        {
            if (MEMORY_FRAGMENT_ORDER.get(i).get() instanceof LostMemoryFragmentItem here
                    && here.scriptKey().equals(scriptKey))
                return ((LostMemoryFragmentItem) MEMORY_FRAGMENT_ORDER.get(i - 1).get()).scriptKey();
        }

        return null;
    }

 // ---------------------------------------------------------------- thought batch
    // Appended at the very end, for the reason stated further up: an entry inserted in the middle of this
    // class shifts the registry ids of every item declared after it, which the game then has to remap for
    // existing saves.

    /**
     * Worldview: Stockholm Syndrome - servitude <b>and</b> endurance, not tiered.
     *
     * <p>Being hurt restores sanity, at most once every 0.5 s (10 ticks). Two details are deliberate: the
     * {@code sanitypd:mania} damage type is excluded, so the per-second mania drain cannot pay for its own
     * escape, and the window is spent by the <b>hurt</b> rather than by the payout - the same reasoning as
     * Stress-Induced Analgesia, whose rate limit exists because every sanity damage type in this mod
     * bypasses the vanilla invulnerability frames.
     */
    public static final RegistryObject<Item> THOUGHT_STOCKHOLM_SYNDROME = DEFERRED_REGISTER.register(
            "thought_stockholm_syndrome",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.WORLDVIEW,
                    java.util.List.of(ThoughtType.SERVITUDE, ThoughtType.ENDURANCE), false));

    /**
     * Methodology: Overdose - endurance, not tiered.
     *
     * <p>Every mood stabilizer's cooldown is shortened, and using one costs a short bout of nausea. The
     * shortcut is applied where the cooldown is started, so it holds for all four stabilizers without
     * touching their own cooldown constants.
     */
    public static final RegistryObject<Item> THOUGHT_OVERDOSE = DEFERRED_REGISTER.register(
            "thought_overdose",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.ENDURANCE), false));

    /**
     * The hidden fragment: the fifth one, and the only one that is not handed out anywhere.
     *
     * <p>Deliberately absent from the creative tab and from {@link #MEMORY_FRAGMENT_ORDER}: it is not part of
     * the story's order, it cannot be picked in creative mode, and its only source is the single allowance a
     * player gets per save for speaking the hidden name with all four story fragments watched through
     * (see {@code item.HiddenMemoryGrant}).
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_HIDDEN = DEFERRED_REGISTER.register(
            "lost_memory_fragment_hidden", () -> new LostMemoryFragmentItem("lost_memory_fragment_hidden"));

    // ------------------------------------------------------------ hallucinations (appended at the very end)
 // The "Hallucination" batch: five row-less thoughts, declared <b>after</b> every
    // existing item on purpose. Appending changes no earlier registry id, so a save keeps every item it
    // already holds - the same reason the delta stabilizer stayed at the end of its own batch.
    //
    // All five are untiered (one sentence, no 1/3/5 ladder) and carry no mindset: the Hallucination type
    // exists and counts like the others, but no threshold unlocks anything for it.

    /**
     * Hallucination: Horse - the first of the four creature visions.
     *
     * <p>While equipped, killing a horse with low sanity drops inner shards, exactly as the darkened animals
     * do. The whole horse family counts except llamas, which belong to the sheep vision - the split is
     * stated once, in {@code loot.InnerLoot}.
     */
    public static final RegistryObject<Item> THOUGHT_HALLUCINATION_HORSE = DEFERRED_REGISTER.register(
            "thought_hallucination_horse",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.HALLUCINATION,
                    java.util.List.of(ThoughtType.HALLUCINATION), false));

    /** Hallucination: Human - villagers, illagers and wandering traders. */
    public static final RegistryObject<Item> THOUGHT_HALLUCINATION_HUMAN = DEFERRED_REGISTER.register(
            "thought_hallucination_human",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.HALLUCINATION,
                    java.util.List.of(ThoughtType.HALLUCINATION), false));

    /** Hallucination: Sheep - llamas (trader llamas included) and goats. */
    public static final RegistryObject<Item> THOUGHT_HALLUCINATION_SHEEP = DEFERRED_REGISTER.register(
            "thought_hallucination_sheep",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.HALLUCINATION,
                    java.util.List.of(ThoughtType.HALLUCINATION), false));

    /** Hallucination: Fish - the water creatures, guardians included. */
    public static final RegistryObject<Item> THOUGHT_HALLUCINATION_FISH = DEFERRED_REGISTER.register(
            "thought_hallucination_fish",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.HALLUCINATION,
                    java.util.List.of(ThoughtType.HALLUCINATION), false));

    /**
     * Hallucination: Convergent Evolution - the batch's finale.
     *
     * <p>Every inner shard and inner clump the holder causes to drop is increased by
     * {@code loot.InnerLoot#CONVERGENT_YIELD}, rounded up: the four creature visions spread inner matter
     * across more of the world, and this is the thought that cashes that spread in.
     */
    public static final RegistryObject<Item> THOUGHT_HALLUCINATION_CONVERGENT_EVOLUTION = DEFERRED_REGISTER.register(
            "thought_hallucination_convergent_evolution",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.HALLUCINATION,
                    java.util.List.of(ThoughtType.HALLUCINATION), false));

    /**
     * The hidden fragment of the player's own name: the sixth story item, and the second that is handed out
     * rather than found.
     *
     * <p>Deliberately absent from the creative tab and from {@link #MEMORY_FRAGMENT_ORDER}, for the same
     * reasons as {@link #LOST_MEMORY_FRAGMENT_HIDDEN}: it is not a step of the story's order, it must not be
     * pickable in creative mode, and its only source is the single allowance a player gets per save for
     * writing nothing but their own game name once the end poem has been watched (see
     * {@code item.HiddenSelfGrant}).
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_SELF = DEFERRED_REGISTER.register(
            "lost_memory_fragment_self", () -> new LostMemoryFragmentItem("lost_memory_fragment_self"));

    /**
     * The hidden fragment of the macaron's name: the seventh story item, and the third that is handed out
     * rather than found.
     *
     * <p>Deliberately absent from the creative tab and from {@link #MEMORY_FRAGMENT_ORDER}, for the same
     * reasons as the two hidden fragments before it: it is not a step of the story's order, it must not be
     * pickable in creative mode, and its only source is the single allowance a player gets per save for
     * writing nothing but the macaron's name once the end poem has been watched (see
     * {@code item.HiddenMacaronGrant}).
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_MACARON = DEFERRED_REGISTER.register(
            "lost_memory_fragment_macaron", () -> new LostMemoryFragmentItem("lost_memory_fragment_macaron"));

    /**
     * The hidden fragment of the inner void: the eighth story item, and the fourth that is handed out rather
     * than found.
     *
     * <p>Deliberately absent from the creative tab and from {@link #MEMORY_FRAGMENT_ORDER}, for the same
     * reasons as the three hidden fragments before it: it is not a step of the story's order, it must not be
     * pickable in creative mode, and its only source is the single allowance a player gets per save for
     * writing nothing but "虚空" once the end poem has been watched (see {@code item.HiddenVoidGrant}).
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_VOID = DEFERRED_REGISTER.register(
            "lost_memory_fragment_void", () -> new LostMemoryFragmentItem("lost_memory_fragment_void"));

    /**
     * The item form of the thought transcription table - this mod's first block item.
     *
     * <p>It is declared here, at the end of the item list, for two reasons: this is the one list every item
     * gate reads (creative tab, item hooks, binding audit), and being last leaves the registry ids of every
     * item declared above it unchanged. The block itself lives in
     * {@link piloser.sanitypd.block.BlockRegistry}; only the item is registered here.
     */
    public static final RegistryObject<Item> THOUGHT_TRANSCRIPTION_TABLE = DEFERRED_REGISTER.register(
            "thought_transcription_table",
            () -> new ThoughtTranscriptionTableItem(BlockRegistry.THOUGHT_TRANSCRIPTION_TABLE.get(), new Item.Properties()));

    /**
     * Shadow Gunpowder: gunpowder that a screaming crawler leaves behind.
     *
     * <p>Its only source is killing one - 60% for a single unit, 40% for two (see
     * {@code loot.InnerLoot#rollShadowGunpowderCount}). Its only use is crafting Shadow TNT, whose recipe is
     * the vanilla TNT layout with this item in place of gunpowder.
     *
 * <p>The texture is the own drawing, used byte for byte (no rescaling, no re-encoding).
     */
    public static final RegistryObject<Item> SHADOW_GUNPOWDER = DEFERRED_REGISTER.register("shadow_gunpowder",
            () -> new Item(new Item.Properties()));

    /**
     * The item form of Shadow TNT.
     *
     * <p>Declared at the very end, like the block item above it: this list is the one every item gate reads,
     * and being last keeps the registry ids of every item declared before it (including the transcription
     * table) exactly where they are.
     */
    public static final RegistryObject<Item> SHADOW_TNT = DEFERRED_REGISTER.register(
            "shadow_tnt",
            () -> new piloser.sanitypd.block.ShadowTntItem(BlockRegistry.SHADOW_TNT.get(), new Item.Properties()));

    /**
     * The hidden fragment of the vessel: the ninth story item, and the fifth that is handed out rather than
     * found.
     *
     * <p>Deliberately absent from the creative tab and from {@link #MEMORY_FRAGMENT_ORDER}, for the same
     * reasons as the four hidden fragments before it: it is not a step of the story's order, it must not be
     * pickable in creative mode, and its only source is the single allowance a player gets per save for
     * writing nothing but "容器" once the inner void's fragment has been watched through (see
     * {@code item.HiddenContainerGrant}).
     *
     * <p>The texture is a byte-for-byte copy of the inner void's, as a placeholder until this one is drawn -
     * the same arrangement the three hidden fragments before it had.
     */
    public static final RegistryObject<Item> LOST_MEMORY_FRAGMENT_CONTAINER = DEFERRED_REGISTER.register(
            "lost_memory_fragment_container", () -> new LostMemoryFragmentItem("lost_memory_fragment_container"));

    /**
     * Thought Container: the transcription table's first crafting ingredient, and the first of the materials
     * that mechanic will consume. Crafted from three glass and one inner clump - the glass bottle's shape with
     * the clump filling the gap in its middle - one per craft (see
     * {@code data/sanitypd/recipes/thought_container.json}).
     *
     * <p>Declared after every fragment and <b>before</b> {@link #HIDDEN_FRAGMENT_ORDER}, because that list is
     * the one declaration that has to stay last in this file: it is a plain list whose entries are the
     * constants above it, so a list written before its own entries would be built while those constants are
     * still null.
     */
    public static final RegistryObject<Item> THOUGHT_CONTAINER = DEFERRED_REGISTER.register("thought_container",
            () -> new Item(new Item.Properties()));

    /**
     * The container's four typed variants: composure, madness, servitude and endurance - one per thought axis
 * that has a tiered ladder, which is what the upgrade recipes will consume.
     *
     * <p>All four are {@link ThoughtContainerItem}: the same 16x16 icon as the plain container plus the
     * enchantment glint. That is the whole difference on the item side - no new art, no rarity change, and the
 * names stay one colour, as it was required ("remember the font colour is consistent").
     *
     * <p>Appended here, before {@link #HIDDEN_FRAGMENT_ORDER}, for the reason spelled out on
     * {@link #THOUGHT_CONTAINER}: that list has to stay the last declaration in this file.
     */
    public static final RegistryObject<Item> THOUGHT_CONTAINER_COMPOSURE = DEFERRED_REGISTER.register(
            "thought_container_composure",
            () -> new ThoughtContainerItem(ThoughtType.COMPOSURE, new Item.Properties()));

    public static final RegistryObject<Item> THOUGHT_CONTAINER_MADNESS = DEFERRED_REGISTER.register(
            "thought_container_madness", () -> new ThoughtContainerItem(ThoughtType.MADNESS, new Item.Properties()));

    public static final RegistryObject<Item> THOUGHT_CONTAINER_SERVITUDE = DEFERRED_REGISTER.register(
            "thought_container_servitude",
            () -> new ThoughtContainerItem(ThoughtType.SERVITUDE, new Item.Properties()));

    public static final RegistryObject<Item> THOUGHT_CONTAINER_ENDURANCE = DEFERRED_REGISTER.register(
            "thought_container_endurance",
            () -> new ThoughtContainerItem(ThoughtType.ENDURANCE, new Item.Properties()));

    // ---------------------------------------------------------------- chaos-restraint batch
 // A chaos-restraint thought is a purely negative one that the story needs anyway (the definition),
    // and it is the axis Depersonalization already counts. Declared after every item above it and before
    // HIDDEN_FRAGMENT_ORDER, for the two reasons spelled out on THOUGHT_CONTAINER: this is the one list every
    // item gate reads, and that fragment list has to stay the last declaration in this file.

    /**
     * Methodology: Non-Suicidal Self-Injury - chaos restraint, tiered.
     *
     * <p>The act: once sanity has stayed below 25% for three minutes, every 60-second period rolls the ladder's
     * chance; five seconds before a successful roll lands the client is told, so it can fade the inner lines
     * out. The wound is five points of this mod's own self-injury damage (never lethal, never mitigated - see
     * {@code SanityDamageTypes#dealSelfInjury}), it gives five points of sanity back and leaves six seconds of
     * slowness. Mechanic: {@code thought.SelfInjury}; presentation: {@code client.SelfInjuryPresentation}.
     */
    public static final RegistryObject<Item> THOUGHT_NON_SUICIDAL_SELF_INJURY = DEFERRED_REGISTER.register(
            "thought_non_suicidal_self_injury",
            () -> new ThoughtItem(new Item.Properties().stacksTo(1), ThoughtCategory.METHODOLOGY,
                    java.util.List.of(ThoughtType.CHAOS_RESTRAINT), true));

    /**
     * Every thought container, plain one first. The transcription table's thought slot accepts these; keeping
     * the set in one place means a later variant is added here once instead of in every filter.
     */
    public static final java.util.List<RegistryObject<Item>> THOUGHT_CONTAINERS = java.util.List.of(
            THOUGHT_CONTAINER,
            THOUGHT_CONTAINER_COMPOSURE,
            THOUGHT_CONTAINER_MADNESS,
            THOUGHT_CONTAINER_SERVITUDE,
            THOUGHT_CONTAINER_ENDURANCE);

    /**
     * The hidden fragments that can be watched through, in declaration order: one bit each in the mask
     * {@code HiddenMemoryGrant} keeps, exactly as {@link #MEMORY_FRAGMENT_ORDER} answers for the four story
     * fragments.
     *
     * <p>Why it exists: a word trigger's prerequisite is <b>not</b> always the end poem. The inner void's is
     * "all four story fragments", and the vessel's is "the inner void's fragment" - so the client has to be
     * able to tell the server which hidden fragments this save has watched, and the server has to be able to
     * ask about one of them by name.
     *
     * <p>⚠️ Declared <b>after</b> every fragment it names (the same rule as the order list above: a list
     * filled by constants declared below it would see nulls), and it is deliberately the last declaration in
     * this file. <b>A new hidden fragment therefore goes in above this list</b> - appended to the item list,
     * but before this line - and is added to it.
     */
    public static final java.util.List<RegistryObject<Item>> HIDDEN_FRAGMENT_ORDER = java.util.List.of(
            LOST_MEMORY_FRAGMENT_HIDDEN,
            LOST_MEMORY_FRAGMENT_SELF,
            LOST_MEMORY_FRAGMENT_MACARON,
            LOST_MEMORY_FRAGMENT_VOID,
            LOST_MEMORY_FRAGMENT_CONTAINER);

    public static void register(IEventBus eventBus)
    {
        DEFERRED_REGISTER.register(eventBus);
    }
}