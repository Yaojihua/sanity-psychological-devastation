package piloser.sanitypd.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import piloser.sanitypd.SanityMod;
import piloser.sanitypd.event.MemoryTapeGuard;

/**
 * The shared decision behind the hidden fragments whose trigger is "a line that is nothing but one of these
 * words".
 *
 * <h2>One decision, several triggers</h2>
 * Two fragments work this way today - the player's own name and the macaron's name - and they differ only in
 * three things: which words count, which flag is spent, and which fragment is handed over. Everything else is
 * the same and must stay the same: the end poem has to have been watched, the allowance is spent exactly once
 * per player per save, the text is never stored, and the player is told something was handed to them. Keeping
 * that in one place is what stops the second trigger from quietly growing a different rule.
 *
 * <h2>Why the words are written out in the source</h2>
 * The macaron's word is a <b>literal</b> Chinese string, not an escape: this mod has already shipped a wrong
 * character once, because a code point was typed from memory and an escape only guarantees "a legal character",
 * never "the character you meant". A literal is also checked at runtime - the probe asserts that the word really
 * is the one the macaron item's own name carries, so a mismatch cannot survive on a real client.
 */
public final class HiddenWordGrant
{
    /** The words the macaron trigger accepts: the item's own name, in both languages. */
    public static final String[] MACARON_WORDS = { "马卡龙", "macaron" };

    /**
     * The words the inner-void trigger accepts.
     *
     * <p>⚠️ Written as <b>literals</b>, never as {@code \\uXXXX} escapes: this project has already shipped the
     * wrong character once because a code point was typed from memory, and an escape only guarantees "a legal
     * character", never "the character you meant".
     *
     * <p>⚠️ The Chinese word is <b>the same phrase the thought transcription table's own description uses</b>
     * ("经由内在虚空的力量…") and <b>the same word the fragment's own name uses</b> ("失落的记忆片段·虚空"): the
 * the trigger was shortened from the longer phrase to the single word the item is named after, to make the
     * hidden fragment easier to reach, so the three still have to keep meaning the same thing. Written as a
     * <b>literal</b>, never as an escape.
     */
    public static final String[] VOID_WORDS = { "虚空", "void" };

    /**
 * The words the vessel trigger accepts: the Chinese word, and the English one the author settled on.
     *
     * <p>⚠️ The English is <b>vessel</b>, not "container": that is the word the story scripts already use for
 * the vessel ("容器"), and the standing instruction is that terminology agrees with the rest of the
     * mod's own text. A trigger that answered to a second spelling would also be a different rule from the one
     * asked for ("nothing but the word").
     */
    public static final String[] CONTAINER_WORDS = { "容器", "vessel" };

    /**
     * Which words count, which fragment is handed over, and which saved-data flag is spent.
     */
    public enum Trigger
    {
        /**
         * Nothing but the player's own game name.
         *
         * <p>Trimmed and case-insensitive: the name was chosen by the game, and having to reproduce its exact
         * capitalisation would be a puzzle rather than a story beat. Anything else in the text - a word before
         * or after it, punctuation - is not a match.
         */
        OWN_NAME("sanitypd_hidden_self_granted", "the fragment of the player's own name")
        {
            @Override
            public boolean matches(ServerPlayer player, String text)
            {
                String name = player.getGameProfile().getName();

                return name != null && !name.isEmpty() && text.trim().equalsIgnoreCase(name);
            }

            @Override
            public RegistryObject<Item> item()
            {
                return ItemRegistry.LOST_MEMORY_FRAGMENT_SELF;
            }
        },

        /**
         * Nothing but the macaron's name, in either language.
         *
 * <p>The coloured names ("Red Macaron", "红色马卡龙") are deliberately <b>not</b> accepted: the author
         * asked for the one word, and a fragment that answers to eight other spellings would be a different
         * rule from the one he described.
         */
        MACARON("sanitypd_hidden_macaron_granted", "the fragment of the macaron word")
        {
            @Override
            public boolean matches(ServerPlayer player, String text)
            {
                String trimmed = text.trim();

                for (String word : MACARON_WORDS)
                {
                    if (trimmed.equalsIgnoreCase(word))
                        return true;
                }

                return false;
            }

            @Override
            public RegistryObject<Item> item()
            {
                return ItemRegistry.LOST_MEMORY_FRAGMENT_MACARON;
            }
        },

        /**
 * Nothing but "虚空" / "Void" (the author shortened this from the longer phrase name).
         *
 * <p>The third word trigger: one silent cancel and one fragment per player per
         * save, and afterwards the same text is an ordinary line.
         *
 * <p>⚠️ <b>Its gate is not the end poem.</b> This one is gated exactly like
         * the hidden fragment "真相" - all <b>four</b> story fragments watched through - which is what
         * {@link #gateSatisfied} returns here. An earlier version reused the poem gate of the other two
         * triggers and therefore fired in a save that had not finished the story.
         */
        INNER_VOID("sanitypd_hidden_void_granted", "the fragment of the inner void")
        {
            @Override
            public boolean matches(ServerPlayer player, String text)
            {
                String trimmed = text.trim();

                for (String word : VOID_WORDS)
                {
                    if (trimmed.equalsIgnoreCase(word))
                        return true;
                }

                return false;
            }

            @Override
            public RegistryObject<Item> item()
            {
                return ItemRegistry.LOST_MEMORY_FRAGMENT_VOID;
            }

            @Override
            public boolean gateSatisfied(ServerPlayer player, boolean poemSeen)
            {
                // The same condition as the hidden tape: all four story fragments watched through in this save.
                return HiddenMemoryGrant.allFragmentsWatched(player);
            }
        },

        /**
         * Nothing but "容器" / "Vessel".
         *
 * <p>The fourth word trigger: one silent cancel and one fragment per player per
         * save, and afterwards the same text is an ordinary line.
         *
 * <p>⚠️ <b>Its prerequisite is neither the poem nor the four fragments</b>: the wording is
         * "after reading the <b>void</b> memory fragment". Read from the spec, not inherited from the trigger
         * above - that inheritance is exactly how the inner void first shipped with the wrong prerequisite.
         */
        CONTAINER("sanitypd_hidden_container_granted", "the fragment of the vessel word")
        {
            @Override
            public boolean matches(ServerPlayer player, String text)
            {
                String trimmed = text.trim();

                for (String word : CONTAINER_WORDS)
                {
                    if (trimmed.equalsIgnoreCase(word))
                        return true;
                }

                return false;
            }

            @Override
            public RegistryObject<Item> item()
            {
                return ItemRegistry.LOST_MEMORY_FRAGMENT_CONTAINER;
            }

            @Override
            public boolean gateSatisfied(ServerPlayer player, boolean poemSeen)
            {
 // The prerequisite, and the one definition of it lives in HiddenMemoryGrant.
                return HiddenMemoryGrant.voidFragmentWatched(player);
            }
        };

        private final String m_key;
        private final String m_label;

        Trigger(String key, String label)
        {
            m_key = key;
            m_label = label;
        }

        /** Whether this text is nothing but one of this trigger's words. */
        public abstract boolean matches(ServerPlayer player, String text);

        /** The fragment this trigger hands over. */
        public abstract RegistryObject<Item> item();

        /** Player-data key of "this save's allowance has been spent". */
        public String key()
        {
            return m_key;
        }

        /** How the log line and the probe refer to this trigger. */
        public String label()
        {
            return m_label;
        }

        /**
         * The words this trigger accepts without knowing who is typing, for the probe and the logs.
         *
         * <p>Empty for {@link #OWN_NAME}, whose word is the player's own name and therefore not a constant.
         */
        public String[] fixedWords()
        {
            if (this == MACARON)
                return MACARON_WORDS.clone();

            if (this == CONTAINER)
                return CONTAINER_WORDS.clone();

            return this == INNER_VOID ? VOID_WORDS.clone() : new String[0];
        }

        /**
         * Whether this player has satisfied this trigger's <b>prerequisite</b>.
         *
         * <p>Each trigger carries its own, because they are genuinely different:
         * <ul>
         *   <li>{@link #OWN_NAME} and {@link #MACARON} require the <b>end poem</b> to have been watched;</li>
         *   <li>{@link #INNER_VOID} requires all <b>four story fragments</b> to have been watched through -
         *       the same condition the hidden tape "真相" uses;</li>
         *   <li>{@link #CONTAINER} requires the <b>inner void's own fragment</b> to have been watched
 * through, which is the chain the design requires.</li>
         * </ul>
         *
         * <p>This is a method rather than one hard-coded rule because hard-coding it is exactly how the inner
         * void first shipped with the wrong prerequisite.
         *
         * @param poemSeen the poem flag, supplied by the caller when it already knows it
         */
        public boolean gateSatisfied(ServerPlayer player, boolean poemSeen)
        {
            return poemSeen;
        }
    }

    private HiddenWordGrant() {}

    /** Spends the allowance and hands the fragment over, when this player is entitled to it. */
    public static boolean tryGrant(ServerPlayer player, String text, Trigger trigger)
    {
        return tryGrant(player, text, trigger, MemoryTapeGuard.hasSeenPoem(player));
    }

    /**
     * The same decision with the poem flag supplied by the caller.
     *
     * <p>Split out because that flag lives in vanilla's private field and is only reachable through an
     * accessor: a caller that already knows it - and the check that has to prove both halves of the rule - does
     * not have to read it a second time.
     */
    public static boolean tryGrant(ServerPlayer player, String text, Trigger trigger, boolean poemSeen)
    {
        CompoundTag data = player.getPersistentData();

        if (data.getBoolean(trigger.key()) || text == null || !trigger.matches(player, text)
                || !trigger.gateSatisfied(player, poemSeen))
            return false;

        data.putBoolean(trigger.key(), true);

        ItemStack stack = new ItemStack(trigger.item().get());

        if (!player.getInventory().add(stack))
            player.drop(stack, false);

        // The same line the forbidden-name allowance shows: from the player's side these are all "something was
        // handed to me", and one wording cannot drift away from another.
        player.displayClientMessage(Component.translatable(HiddenMemoryGrant.GRANTED_KEY), false);

        SanityMod.LOGGER.info("[MEMORY] {} was handed to {} - this save's allowance is spent",
                trigger.label(), player.getGameProfile().getName());

        return true;
    }

    /** Whether this player may still earn this trigger's fragment in this save. */
    public static boolean allowanceAvailable(ServerPlayer player, Trigger trigger)
    {
        return !player.getPersistentData().getBoolean(trigger.key());
    }

    /** Whether this player has already spent this trigger's allowance, for the probe and the logs. */
    public static boolean alreadySpent(ServerPlayer player, Trigger trigger)
    {
        return player.getPersistentData().getBoolean(trigger.key());
    }
}
