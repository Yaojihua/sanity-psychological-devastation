package piloser.sanitypd.recipe;

import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import piloser.sanitypd.SanityMod;

/**
 * An ingredient that accepts any potion of one of a set of potion types, whatever else is on the stack.
 *
 * <h2>Why this exists instead of a built-in ingredient</h2>
 * The two thoughts "Fight or Flight" and "Psychomotor Agitation" are shaped the same way and differ only in
 * the potion in the top slot (strength versus swiftness) - which is exactly what a custom ingredient is for.
 * The obvious approaches do not work:
 *
 * <ul>
 *   <li><b>Vanilla {@code "nbt"}</b>: the vanilla ingredient deserializer does not read an {@code nbt}
 *       field at all. Measured from the compiled class: the string {@code nbt} does not appear once in its
 *       constant pool, so the field is silently ignored and the two recipes collapse into the same
 *       ingredient - the second one is then dropped as a duplicate.</li>
 *   <li><b>Forge {@code forge:partial_nbt}</b>: only accepts a single {@code item} (or {@code items}) plus
 *       one {@code nbt} compound. That cannot express "any of the brewed variants", and a wrong shape is a
 *       hard recipe parse error ("Must set either 'item' or 'items'").</li>
 *   <li><b>Forge {@code forge:nbt}</b>: matches the NBT <b>exactly</b>, and a brewed potion carries more
 *       than the {@code Potion} id (a {@code CustomPotionEffects} list), so a freshly brewed potion would
 *       not match the recipe that is meant for it.</li>
 * </ul>
 *
 * <p>So the matching rule is stated here directly: the stack is a potion item and its {@code Potion} tag is
 * one of the allowed ids. Everything else on the stack is ignored, which is what "any variant" has to mean.
 *
 * <p>{@link #isSimple()} is <b>false</b>: a simple ingredient may be matched by item alone, and these two
 * differ only by NBT.
 *
 * <h2>How it is registered</h2>
 * Ingredient serializers are not a Forge registry - {@code CraftingHelper} keeps its own map - so there is
 * no {@code DeferredRegister} to use here. Forge registers its own the same way this class does: from a
 * {@link RegisterEvent} handler for the {@code recipe_serializer} registry, because that event is the
 * documented point at which datapack-facing types may be added.
 */
public final class PotionIngredient extends AbstractIngredient
{
    /** The serializer, also this ingredient's identity in {@code CraftingHelper}. */
    public static final Serializer INSTANCE = new Serializer();

    /** Registry name of the ingredient type as it appears in recipe JSON. */
    public static final ResourceLocation ID = new ResourceLocation(SanityMod.MODID, "potion");

    /** Potion ids this ingredient accepts, in the order they were written. */
    private final Set<ResourceLocation> m_potions;

    /**
     * @param potions the accepted potion ids; must not be empty, or the ingredient could never match
     */
    public PotionIngredient(Set<ResourceLocation> potions)
    {
        // The value list is not decoration: `Ingredient#test` is "does any of my values match this stack",
        // and `getItems()` - which JEI and the recipe book read - is the union of those same values. An early
        // version passed a single plain POTION here and overrode test() to ignore the list entirely, and JEI
        // showed the slot as an uncraftable potion, because the stack it wanted to offer was not among the
        // values. So the list is built from the potions this ingredient accepts, and test() consults it.
        super(valuesFor(potions));

        if (potions.isEmpty())
            throw new IllegalArgumentException("a potion ingredient needs at least one potion id");

        m_potions = java.util.Set.copyOf(potions);
    }

    /**
     * One item value per accepted potion, carrying the potion it stands for.
     *
     * <p>Built here rather than in the constructor's call to {@code super} because a constructor argument
     * cannot reference the instance being built.
     */
    private static java.util.stream.Stream<Ingredient.Value> valuesFor(Set<ResourceLocation> potions)
    {
        return potions.stream().map(id ->
        {
            Potion potion = ForgeRegistries.POTIONS.getValue(id);
            ItemStack stack = new ItemStack(Items.POTION);

            // The tag is what makes this stack "a Potion of Strength" rather than "a potion".
            PotionUtils.setPotion(stack, potion == null ? Potions.WATER : potion);
            return (Ingredient.Value) new Ingredient.ItemValue(stack);
        });
    }

    /** The potion ids this ingredient accepts. */
    public Set<ResourceLocation> potions()
    {
        return m_potions;
    }

    /**
     * Registers this ingredient type with Forge's crafting helper.
     *
     * <p>Called once from the mod's constructor through a {@link RegisterEvent} listener for the
     * {@code recipe_serializer} registry.
     */
    public static void register(RegisterEvent event)
    {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS))
            CraftingHelper.register(ID, INSTANCE);
    }

    /**
     * Whether this stack is one of the potions the recipe accepts.
     *
     * <p>The base implementation already answers this from the value list built in the constructor, so this
     * override exists only to state the rule in one readable place and to reject a stack with no Potion tag
     * at all (a plain water bottle is a potion item, and {@code PotionUtils} reports water for it, which is
     * not in any of these lists - but saying so explicitly keeps the rule visible here).
     */
    @Override
    public boolean test(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof PotionItem))
            return false;

        // Reads whatever Potion tag the stack carries. A stack with no tag at all (a plain water bottle)
        // reports the vanilla water potion, which is not in any of these lists, so it matches nothing.
        Potion potion = PotionUtils.getPotion(stack);

        return m_potions.contains(ForgeRegistries.POTIONS.getKey(potion));
    }

    /** Never simple: the two recipes differ only by NBT, so item-level matching would conflate them. */
    @Override
    public boolean isSimple()
    {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer()
    {
        return INSTANCE;
    }

    @Override
    public JsonElement toJson()
    {
        JsonObject json = new JsonObject();

        json.addProperty("type", ID.toString());

        JsonArray ids = new JsonArray();

        for (ResourceLocation id : m_potions)
            ids.add(id.toString());

        json.add("potions", ids);
        return json;
    }

    /** Reads {@code {"type": "sanitypd:potion", "potions": ["minecraft:strength", ...]}}. */
    public static final class Serializer implements IIngredientSerializer<PotionIngredient>
    {
        @Override
        public PotionIngredient parse(JsonObject json)
        {
            JsonArray ids = GsonHelper.getAsJsonArray(json, "potions");
            Set<ResourceLocation> potions = new LinkedHashSet<>();

            for (JsonElement element : ids)
            {
                ResourceLocation id = ResourceLocation.tryParse(GsonHelper.convertToString(element, "potions[]"));

                if (id == null)
                    throw new JsonSyntaxException("Not a valid potion id: " + element);

                // Checked here rather than at match time: a typo in a recipe should be a load error with the
                // offending id in it, not a recipe that silently never matches.
                if (!ForgeRegistries.POTIONS.containsKey(id))
                    throw new JsonSyntaxException("Unknown potion: " + id);

                potions.add(id);
            }

            return new PotionIngredient(potions);
        }

        @Override
        public PotionIngredient parse(FriendlyByteBuf buffer)
        {
            int size = buffer.readVarInt();
            Set<ResourceLocation> potions = new LinkedHashSet<>();

            for (int i = 0; i < size; i++)
                potions.add(buffer.readResourceLocation());

            return new PotionIngredient(potions);
        }

        @Override
        public void write(FriendlyByteBuf buffer, PotionIngredient ingredient)
        {
            buffer.writeVarInt(ingredient.m_potions.size());

            for (ResourceLocation id : ingredient.m_potions)
                buffer.writeResourceLocation(id);
        }
    }
}
