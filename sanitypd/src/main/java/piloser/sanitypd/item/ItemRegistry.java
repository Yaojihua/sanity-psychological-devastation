package piloser.sanitypd.item;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.entity.EntityRegistry;
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

    public static void register(IEventBus eventBus)
    {
        DEFERRED_REGISTER.register(eventBus);
    }
}