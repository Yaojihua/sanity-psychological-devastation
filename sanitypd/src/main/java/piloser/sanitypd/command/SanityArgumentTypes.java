package piloser.sanitypd.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier argument types defined by this mod - only the tier enum.
 *
 * <h2>Why text arguments use no custom type</h2>
 * The player writes a whole free-form sentence, which contains spaces and punctuation. Vanilla
 * {@code StringArgumentType.greedyString()} already consumes the entire rest of the line, and Forge
 * plus vanilla already registered its serializer on the server, so the client always recognises it.
 * A custom type is risky: if its {@code ArgumentTypeInfo} is not registered, the <b>whole command
 * tree fails to sync to the client</b> - not just that one command, but every command.
 * Text arguments therefore stay on vanilla {@code greedyString}.
 *
 * <h2>Why the tier argument may be custom</h2>
 * For {@code /sanity hint <tier>} the command tree resolves the input into a {@link HintTier} constant
 * at <b>build time</b>, hanging one subcommand off each value, so what remains in the tree are
 * {@code literal} nodes. The client only syncs those literals and never needs to know this class.
 */
public final class SanityArgumentTypes
{
    private SanityArgumentTypes() {}

    /**
     * The inner-voice tiers, ordered the same way as the client {@code MentalHintManager}
     * (0 mild / 1 severe / 2 deep).
     *
     * <p>{@link #EXPIRY} is the separate "immunity is about to expire" warning pool. It is not a madness
     * tier: nothing selects it by sanity value, it maps to the client side index {@code TIER_COUNT}
     * ({@code MentalHintManager.INDEX_EXPIRY}), it ships empty and it is only ever shown during the final 5
     * seconds of the mania-immunity buff. Keeping it as its own literal is what lets {@code /sanity hint
     * expiry add <text>} manage it without touching the three madness pools.
     */
    public enum HintTier
    {
        MILD(0),
        SEVERE(1),
        DEEP(2),
        EXPIRY(3);

        private final int m_index;

        HintTier(int index)
        {
            m_index = index;
        }

        /** The tier index passed to {@code MentalHintManager}. */
        public int index()
        {
            return m_index;
        }

        /** The literal typed in the command, all lowercase ASCII; Brigadier handles case insensitivity. */
        public String literal()
        {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Reverse lookup by tier index; out-of-range values return {@link #MILD}. */
        public static HintTier byIndex(int index)
        {
            for (HintTier tier : values())
            {
                if (tier.m_index == index)
                    return tier;
            }
            return MILD;
        }

        /** Translation key naming this tier (mild / severe / deep / expiry). */
        public String translationKey()
        {
            return "commands.sanity.hint.tier." + m_index;
        }

        public static Collection<String> literals()
        {
            return Arrays.asList("mild", "severe", "deep", "expiry");
        }
    }

    /**
     * Argument type only used to parse the mild / severe / deep enum.
     *
     * <p>As the class comment explains, it is only used while <b>building</b> the command tree: the
     * result is immediately turned into a {@link HintTier} constant attached to a literal subcommand,
     * so no serializer registration is needed.
     */
    public static class EnumArgumentType<E extends Enum<E>> implements ArgumentType<E>
    {
        private final Class<E> m_class;

        private EnumArgumentType(Class<E> clazz)
        {
            m_class = clazz;
        }

        public static <E extends Enum<E>> EnumArgumentType<E> enumArg(Class<E> clazz)
        {
            return new EnumArgumentType<>(clazz);
        }

        public static <E extends Enum<E>> E getEnum(CommandContext<?> context, String name, Class<E> clazz)
        {
            return context.getArgument(name, clazz);
        }

        @Override
        public E parse(StringReader reader) throws CommandSyntaxException
        {
            int start = reader.getCursor();
            String name = reader.readUnquotedString();

            for (E constant : m_class.getEnumConstants())
            {
                if (constant.name().equalsIgnoreCase(name))
                    return constant;
            }

            reader.setCursor(start);
            throw new SimpleCommandExceptionType(Component.translatable("commands.sanity.hint.invalid_tier"))
                    .createWithContext(reader);
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder)
        {
            String remaining = builder.getRemainingLowerCase();

            for (E constant : m_class.getEnumConstants())
            {
                String name = constant.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(remaining))
                    builder.suggest(name);
            }

            return builder.buildFuture();
        }

        @Override
        public Collection<String> getExamples()
        {
            return HintTier.literals();
        }
    }

    /** Convenience entry point for {@code SharedSuggestionProvider} (tier literal completion). */
    public static CompletableFuture<Suggestions> suggestTiers(SuggestionsBuilder builder)
    {
        return SharedSuggestionProvider.suggest(HintTier.literals(), builder);
    }
}
