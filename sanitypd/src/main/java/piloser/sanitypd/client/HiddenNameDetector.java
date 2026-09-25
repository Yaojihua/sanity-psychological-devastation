package piloser.sanitypd.client;

/**
 * Name detection half of the hidden-name easter egg, deliberately written as a plain,
 * side-agnostic utility class.
 *
 * <p>The crash half ({@link HiddenNameWatcher}) must be client only because it calls
 * {@code Runtime.halt}. Tagging it with {@code @OnlyIn(Dist.CLIENT)} makes Forge's
 * {@code RuntimeDistCleaner} reject it at class load time on a dedicated server
 * ({@code Attempted to load class ... for invalid dist DEDICATED_SERVER}), which would turn
 * "may a command class reference it" into an implicit contract that can only be proven by
 * disassembly or a live run.
 *
 * <p>Deciding whether a string contains the name needs no client at all, so it lives here in a class
 * with no {@code @OnlyIn} and no client types. That keeps it loadable and verifiable on a dedicated
 * server, and confines the dangerous line to a method only the client executes.
 *
 * <p>Matching rule: strip every character that is neither a letter nor a digit (CJK characters count
 * as letters and are kept), lowercase the result, then test whether it contains either target word.
 * Interleaved punctuation and mixed case still match; there is no length limit.
 */
public final class HiddenNameDetector
{
    /** ASCII target word (compared in lower case). */
    public static final String NAME_ASCII = "cacomorth";

    /**
     * Chinese target word, written as escapes on purpose.
     *
     * <p>The public source tree must not spell the name out: it is the one hidden easter egg this mod
     * ships, and a literal here would hand it to anyone who opens the repository. The escapes keep the
     * matching behaviour identical while leaving the name unreadable to a casual reader. Do not replace
     * them with the literal characters.
     */
    public static final String NAME_ZH = "\u5361\u5580\u83AB\u65AF";

    private HiddenNameDetector() {}

    /**
     * Tests whether the text mentions the name.
     *
     * @param text raw player-supplied text, may be {@code null}
     * @return {@code true} when the name was found
     */
    public static boolean mentionsTheName(String text)
    {
        if (text == null || text.isEmpty())
            return false;

        String normalized = normalize(text);
        return normalized.contains(NAME_ASCII) || normalized.contains(NAME_ZH);
    }

    /**
     * Removes every non-alphanumeric character and lowercases the rest (CJK characters are kept).
     *
     * <p>{@link Character#isLetterOrDigit(char)} is used instead of an explicit whitelist because CJK
     * characters are {@code LETTER_OTHER} in Java, so spaced-out spellings still normalize to the
     * plain word.
     */
    public static String normalize(String text)
    {
        StringBuilder sb = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c))
                sb.append(Character.toLowerCase(c));
        }

        return sb.toString();
    }
}
