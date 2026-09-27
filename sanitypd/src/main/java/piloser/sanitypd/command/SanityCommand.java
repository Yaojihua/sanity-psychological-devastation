package piloser.sanitypd.command;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import piloser.sanitypd.capability.ISanity;
import piloser.sanitypd.capability.Sanity;
import piloser.sanitypd.capability.SanityProvider;
import piloser.sanitypd.config.DimensionConfig;
import piloser.sanitypd.damage.SanityDamageTypes;
import piloser.sanitypd.event.TestSpawnGuard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegistryObject;
import piloser.sanitypd.SanityMod;

/**
 * The /sanity command.
 *
 * <ul>
 *   <li>{@code /sanity <value>}: <b>shortest form</b>, sets the caller's own sanity points (e.g. {@code /sanity 50})</li>
 *   <li>{@code /sanity set [value]} / {@code /sanity set <targets> <value>}: set sanity points (any mob)</li>
 *   <li>{@code /sanity add [value]} / {@code /sanity add <targets> <value>}: add or subtract sanity points (any mob)</li>
 *   <li>{@code /sanity get <targets>}: print current / maximum sanity (debug)</li>
 *   <li>{@code /sanity psychic <targets> <amount>}: deal psychic damage (full conversion rules apply, debug)</li>
 *   <li>{@code /sanity truedamage <targets> <amount>}: deal true damage (debug)</li>
 *   <li>{@code /sanity config reload}: reload the per-dimension config</li>
 *   <li>{@code /sanity nospawn on|off|status}: <b>testing aid</b>, clears and blocks every
 *       "non-inner" monster while active (slimes included; inner entities are allowed through)</li>
 *   <li>{@code /sanity innerspawn on|off|status}: <b>testing aid</b>, the opposite: clears and blocks
 *       <b>inner entities</b>; independent of {@code nospawn} and stackable with it</li>
 *   <li>{@code /sanity resist set|add|get|clear <targets> <0~100>}:
 *       psychic resistance, which only reduces the "psychic damage drains sanity" part</li>
 *   <li>{@code /sanity hint <mild|severe|deep> add|remove|list|clear|show}:
 *       custom inner hints (stored client-side) and an <b>immediate preview</b></li>
 * </ul>
 */
public class SanityCommand
{
    /** Upper damage bound for the debug commands; a wide range makes overflow conversion easy to test. */
    private static final float MAX_DEBUG_AMOUNT = 100000.0f;

    /**
     * True damage dealt to the sender when the hidden name is spoken on the <b>server</b> side.
     *
     * <p>This is the multiplayer counterpart of the hidden easter egg. The client always shuts itself
     * down, but a dedicated server must never do that - and the server is the side that owns the
     * authoritative player, so it answers with lethal damage instead. See
     * {@link #applyForbiddenNamePunishment}.
     */
    private static final float FORBIDDEN_NAME_PUNISHMENT = 20000.0f;

    /**
     * Health a player is left with when macarons buy him out of the forbidden-name outcome.
     *
     * <p>The brief is explicit that this is a <b>set</b>, not a reduction: whatever the player's health
     * was, it becomes exactly this.
     */
    public static final float ESCAPE_HEALTH = 1.0f;

    /**
     * Language key of the line shown to the player who paid with macarons. Only the sender sees it, so
     * the other players learn nothing.
     */
    public static final String ESCAPE_MESSAGE_KEY = "gui." + SanityMod.MODID + ".egg.escape";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sanity")
                .requires(stack -> stack.hasPermission(2));

        // ---- /sanity set ----
        root.then(Commands.literal("set")
                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, Sanity.MAX_SANITY))
                        .executes(stack -> setSanity(stack.getSource(),
                                Collections.singleton(stack.getSource().getPlayerOrException()),
                                FloatArgumentType.getFloat(stack, "value"))))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0f, Sanity.MAX_SANITY))
                                .executes(stack -> setSanity(stack.getSource(),
                                        EntityArgument.getEntities(stack, "targets"),
                                        FloatArgumentType.getFloat(stack, "value"))))));

        // ---- /sanity add ----
        root.then(Commands.literal("add")
                .then(Commands.argument("value", FloatArgumentType.floatArg(-Sanity.MAX_SANITY, Sanity.MAX_SANITY))
                        .executes(stack -> addSanity(stack.getSource(),
                                Collections.singleton(stack.getSource().getPlayerOrException()),
                                FloatArgumentType.getFloat(stack, "value"))))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-Sanity.MAX_SANITY, Sanity.MAX_SANITY))
                                .executes(stack -> addSanity(stack.getSource(),
                                        EntityArgument.getEntities(stack, "targets"),
                                        FloatArgumentType.getFloat(stack, "value"))))));

        // ---- /sanity get <targets> (debug: read sanity) ----
        root.then(Commands.literal("get")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(stack -> getSanity(stack.getSource(), EntityArgument.getEntities(stack, "targets")))));

        // ---- /sanity state <targets> (debug: read sanity plus confusion / mania state) ----
        root.then(Commands.literal("state")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(stack -> getState(stack.getSource(), EntityArgument.getEntities(stack, "targets")))));

        // ---- /sanity psychic <targets> <amount> (debug: deal psychic damage) ----
        root.then(Commands.literal("psychic")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, MAX_DEBUG_AMOUNT))
                                .executes(stack -> applyPsychic(stack.getSource(),
                                        EntityArgument.getEntities(stack, "targets"),
                                        FloatArgumentType.getFloat(stack, "amount"))))));

        // ---- /sanity truedamage <targets> <amount> (debug: deal true damage) ----
        root.then(Commands.literal("truedamage")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, MAX_DEBUG_AMOUNT))
                                .executes(stack -> applyTrueDamage(stack.getSource(),
                                        EntityArgument.getEntities(stack, "targets"),
                                        FloatArgumentType.getFloat(stack, "amount"))))));

        // ---- /sanity <value> (shortest form: set the caller's own sanity) ----
        // It does not clash with the literal subcommands above: Brigadier matches literals first,
        // "set" and friends are not valid numbers, and a plain number is never parsed as an entity
        // selector, so /sanity 50 always lands here.
        root.then(Commands.argument("value", FloatArgumentType.floatArg(0f, Sanity.MAX_SANITY))
                .executes(stack -> setSanity(stack.getSource(),
                        Collections.singleton(stack.getSource().getPlayerOrException()),
                        FloatArgumentType.getFloat(stack, "value"))));

        // ---- /sanity config reload ----
        root.then(Commands.literal("config")
                .then(Commands.literal("reload")
                        .executes(stack -> reloadConfig(stack.getSource()))));

        // ---- /sanity nospawn on|off|status (testing aid: clear and block "non-inner" monsters) ----
        root.then(Commands.literal("nospawn")
                .then(Commands.literal("on").executes(stack -> setNoSpawn(stack.getSource(), true)))
                .then(Commands.literal("off").executes(stack -> setNoSpawn(stack.getSource(), false)))
                .then(Commands.literal("status").executes(stack -> noSpawnStatus(stack.getSource()))));

        // ---- /sanity innerspawn on|off|status (testing aid: clear and block inner entities) ----
        // Independent of nospawn; enabling both means no mobs at all.
        root.then(Commands.literal("innerspawn")
                .then(Commands.literal("on").executes(stack -> setInnerNoSpawn(stack.getSource(), true)))
                .then(Commands.literal("off").executes(stack -> setInnerNoSpawn(stack.getSource(), false)))
                .then(Commands.literal("status").executes(stack -> innerNoSpawnStatus(stack.getSource()))));

        // ---- /sanity resist set|add|get|clear <targets> <0~100> ----
        registerResist(root);

        // ---- /sanity hint ... (custom inner hints plus immediate preview) ----
        registerHint(root);

        dispatcher.register(root);
    }

    // ================================================================== psychic resistance

    /** Valid resistance range: 0 = no reduction, 100 = fully immune (only the sanity-drain part is affected). */
    private static final float MAX_RESISTANCE = 100f;

    /** Three-part command form: {@code /sanity resist <mode> <targets> <value>}. */
    private enum ResistMode
    {
        SET, ADD, CLEAR
    }

    /** Sign of the arithmetic. */
    private enum ValueMode
    {
        ADD, SUBTRACT
    }

    private static void registerResist(LiteralArgumentBuilder<CommandSourceStack> root)
    {
        var resist = Commands.literal("resist");

        for (ResistMode mode : ResistMode.values())
        {
            var modeNode = Commands.literal(mode.name().toLowerCase(Locale.ROOT));

            if (mode == ResistMode.CLEAR)
            {
                modeNode.then(Commands.argument("targets", EntityArgument.entities())
                        .executes(stack -> applyResistance(stack.getSource(),
                                EntityArgument.getEntities(stack, "targets"), mode, 0f)));
            }
            else
            {
                for (ValueMode valueMode : ValueMode.values())
                {
                    float min = valueMode == ValueMode.ADD ? -MAX_RESISTANCE : 0f;

                    modeNode.then(Commands.literal(valueMode == ValueMode.ADD ? "add" : "subtract")
                            .then(Commands.argument("targets", EntityArgument.entities())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg(min, MAX_RESISTANCE))
                                            .executes(stack -> applyResistance(stack.getSource(),
                                                    EntityArgument.getEntities(stack, "targets"),
                                                    mode,
                                                    valueMode == ValueMode.ADD
                                                            ? FloatArgumentType.getFloat(stack, "value")
                                                            : -FloatArgumentType.getFloat(stack, "value"))))));
                }
            }

            resist.then(modeNode);
        }

        // /sanity resist get <targets>
        resist.then(Commands.literal("get")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(stack -> getResistance(stack.getSource(),
                                EntityArgument.getEntities(stack, "targets")))));

        root.then(resist);
    }

    /**
     * Set, add or clear psychic resistance.
     *
     * <p>There are two backing stores, routed by entity type:
     * <ul>
     *   <li><b>normal mobs / players</b>: the capability field {@code psychicResistance} (0~1, a percentage reduction);</li>
     *   <li><b>inner entities</b>: the entity's own "independent psychic resistance" points
     *       (stalker 10 / crawler 20 / lurker 15), which scale the "psychic damage -> 2.5x true damage"
     *       conversion by {@code points / 100}.</li>
     * </ul>
     * Both use the same 0~100 point scale, so the command needs only one set of arguments.
     */
    private static int applyResistance(CommandSourceStack stack, Collection<? extends Entity> targets,
                                       ResistMode mode, float value)
    {
        int count = 0;
        Entity last = null;
        float lastValue = 0f;
        boolean lastInner = false;

        for (Entity entity : targets)
        {
            if (!(entity instanceof LivingEntity living))
                continue;

            Float now = setResistanceOn(living, mode, value);
            if (now == null)
                continue;

            last = living;
            lastValue = now;
            lastInner = living instanceof piloser.sanitypd.entity.InnerEntity;
            count++;
        }

        if (count == 0)
        {
            stack.sendFailure(Component.translatable("commands.sanity.resist.no_target"));
            return 0;
        }

        final Entity shown = last;
        final float shownValue = lastValue;
        final boolean inner = lastInner;
        final int total = count;
        final String verb = mode == ResistMode.CLEAR ? "clear" : (mode == ResistMode.SET ? "set" : "add");
        final String scope = inner ? ".inner" : "";

        if (count == 1)
            stack.sendSuccess(() -> Component.translatable("commands.sanity.resist." + verb + ".single" + scope,
                    shown.getDisplayName(), format(shownValue)), true);
        else
            stack.sendSuccess(() -> Component.translatable("commands.sanity.resist." + verb + ".multiple",
                    shownValue, total), true);

        return count;
    }

    /**
     * Write resistance onto a single entity.
     *
     * @return the value after writing (0~100); {@code null} = the entity has no resistance store at all
     *         (which should not happen)
     */
    private static Float setResistanceOn(LivingEntity living, ResistMode mode, float value)
    {
        if (living instanceof piloser.sanitypd.entity.InnerEntity inner)
        {
            float now = inner.getPsychicResistance();

            if (mode == ResistMode.CLEAR)
                now = inner.defaultPsychicResistance();
            else if (mode == ResistMode.ADD)
                now = Math.max(0f, Math.min(MAX_RESISTANCE, now + value));
            else
                now = Math.max(0f, Math.min(MAX_RESISTANCE, value));

            inner.setPsychicResistance(now);
            return now;
        }

        ISanity cap = living.getCapability(SanityProvider.CAP).orElse(null);
        if (cap == null)
            return null;

        float now = cap.getPsychicResistance() * MAX_RESISTANCE;

        if (mode == ResistMode.CLEAR)
            now = 0f;
        else if (mode == ResistMode.ADD)
            now = Math.max(0f, Math.min(MAX_RESISTANCE, now + value));
        else
            now = Math.max(0f, Math.min(MAX_RESISTANCE, value));

        cap.setPsychicResistance(now / MAX_RESISTANCE);
        return now;
    }

    /** Print the target's current psychic resistance. */
    private static int getResistance(CommandSourceStack stack, Collection<? extends Entity> targets)
    {
        int found = 0;

        for (Entity entity : targets)
        {
            if (!(entity instanceof LivingEntity living))
                continue;

            if (living instanceof piloser.sanitypd.entity.InnerEntity inner)
            {
                final Entity shown = living;
                final float value = inner.getPsychicResistance();
                stack.sendSuccess(() -> Component.translatable("commands.sanity.resist.get.inner",
                        shown.getDisplayName(), format(value)), false);
                found++;
                continue;
            }

            ISanity cap = living.getCapability(SanityProvider.CAP).orElse(null);
            if (cap == null)
                continue;

            final Entity shown = living;
            final float value = cap.getPsychicResistance() * MAX_RESISTANCE;
            stack.sendSuccess(() -> Component.translatable("commands.sanity.resist.get.success",
                    shown.getDisplayName(), format(value)), false);
            found++;
        }

        if (found == 0)
            stack.sendFailure(Component.translatable("commands.sanity.resist.no_target"));

        return found;
    }

    // ================================================================== inner hints

    /**
     * {@code /sanity hint ...}: custom inner hints plus immediate preview.
     *
     * <ul>
     *   <li>{@code add <mild|severe|deep> <text>}: add a line to a tier (the text may contain spaces, greedy string)</li>
     *   <li>{@code remove <tier> <index>}: delete a custom line (built-in lines cannot be removed; indexes come from {@code list})</li>
     *   <li>{@code list <tier>}: list every candidate line of that tier with its index</li>
     *   <li>{@code clear <tier>}: clear the custom lines of that tier (falling back to the default pool)</li>
     *   <li>{@code show <tier>}: show one line of that tier in the centre of the screen <b>immediately</b> (6 seconds)</li>
     * </ul>
     *
     * <p>The hint pool and the rendering are <b>client-side</b> only
     * ({@code config/sanitypd_mental_hints.json}), so the methods that do the real work are dispatched
     * to the client; that way loading {@code SanityCommand} on a dedicated server never touches client classes.
     */
    private static void registerHint(LiteralArgumentBuilder<CommandSourceStack> root)
    {
        var hint = Commands.literal("hint");

        for (SanityArgumentTypes.HintTier tier : SanityArgumentTypes.HintTier.values())
        {
            String literal = tier.literal();

            // add <text>
            hint.then(Commands.literal(literal).then(Commands.literal("add")
                    .then(Commands.argument("text", StringArgumentType.greedyString())
                            .executes(stack -> addHint(stack.getSource(), tier,
                                    StringArgumentType.getString(stack, "text"))))));

            // remove <index>
            hint.then(Commands.literal(literal).then(Commands.literal("remove")
                    .then(Commands.argument("index", IntegerArgumentType.integer(1))
                            .executes(stack -> removeHint(stack.getSource(), tier,
                                    IntegerArgumentType.getInteger(stack, "index"))))));

            // list
            hint.then(Commands.literal(literal).then(Commands.literal("list")
                    .executes(stack -> listHints(stack.getSource(), tier))));

            // clear
            hint.then(Commands.literal(literal).then(Commands.literal("clear")
                    .executes(stack -> clearHints(stack.getSource(), tier))));

            // show (immediate display)
            hint.then(Commands.literal(literal).then(Commands.literal("show")
                    .executes(stack -> showHint(stack.getSource(), tier))));
        }

        // debug <on|off>: draws the on-screen centre-line markers (see GuiHandler#DEBUG_CENTRE_MARKERS).
        // It exists to tell "the line was drawn somewhere else / was not visible" apart from "the draw never
        // happened", which the log alone cannot answer.
        hint.then(Commands.literal("debug")
                .then(Commands.literal("on").executes(stack -> setCentreDebug(stack.getSource(), true)))
                .then(Commands.literal("off").executes(stack -> setCentreDebug(stack.getSource(), false))));

        root.then(hint);
    }

    /** {@code /sanity hint debug <on|off>}: turns the centre-line markers on or off (client-side). */
    private static int setCentreDebug(CommandSourceStack stack, boolean on)
    {
        return runOnClient(stack, () ->
        {
            piloser.sanitypd.client.GuiHandler.DEBUG_CENTRE_MARKERS = on;
            stack.sendSuccess(() -> Component.literal("[CENTRE] markers " + (on ? "ON" : "OFF")), false);
            return ClientResult.ok(1);
        });
    }

    /** Command tier -> tier index used by the client-side {@code MentalHintManager}. */
    private static int tierIndex(SanityArgumentTypes.HintTier tier)
    {
        return tier.index();
    }

    /**
     * Return value of a client-only operation: a status code plus an optional error language key.
     *
     * @param code     command return value (0 = failure)
     * @param errorKey when non-null, this language key is echoed through {@code sendFailure}
     */
    private record ClientResult(int code, String errorKey)
    {
        static ClientResult ok(int code)
        {
            return new ClientResult(code, null);
        }

        static ClientResult error(String key)
        {
            return new ClientResult(0, key);
        }
    }

    /** A client-only operation. */
    private interface ClientCall
    {
        ClientResult run();
    }

    /**
     * Safely dispatches a client-only hint operation.
     *
     * <p><b>Why not {@code DistExecutor}</b>: Forge 1.20.1 removed it (since 1.19 the environment is
     * compared directly through {@code FMLEnvironment.dist}). This small helper is equivalent:
     * <ul>
     *   <li>it only runs on the client, so loading this class on a dedicated server never calls the client half;</li>
     *   <li>it turns a validation-failure language key into a {@code sendFailure} echo;</li>
     *   <li>a server-side call gets a clear message ({@code client_only}) instead of failing silently.</li>
     * </ul>
     */
    private static int runOnClient(CommandSourceStack stack, ClientCall clientAction)
    {
        if (FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.CLIENT)
        {
            stack.sendFailure(Component.translatable("commands.sanity.hint.client_only"));
            return 0;
        }

        ClientResult result = clientAction.run();

        if (result.errorKey() != null)
        {
            stack.sendFailure(Component.translatable(result.errorKey()));
            return 0;
        }

        return result.code();
    }

    /**
     * Server half of the hidden easter egg: swallow the command and punish the speaker.
     *
     * <h3>Why the server must not crash</h3>
     * The client half of the easter egg ({@code client/HiddenNameWatcher}) ends in
     * {@code Runtime.halt}, which is correct for the game window - it simply disappears - but a
     * dedicated server that halts would drop every other player and never write the chunks it still
     * holds in memory. So the crash helper must only ever be reachable from the client branch below,
     * and the server answers with something it is allowed to do: damage.
     *
     * <h3>The real discriminator: who sent the command</h3>
     * This is <b>not</b> "which side is executing" and the dist is deliberately not consulted. A chat
     * command is executed by the server even in single player
     * ({@code ServerGamePacketListenerImpl#performChatCommand} -> {@code Commands#performCommand} on the
     * server thread), so "ran on the server thread" and "ran on this machine" do not tell a player apart
     * from a command block. What does tell them apart is the sender: a player command carries a
     * {@link ServerPlayer}, while a command block, a command-block minecart and the server console all
     * carry <b>no entity at all</b>.
     *
     * <h3>What it does</h3>
     * Nothing observable through the command: no success message, no failure message, and the text is
     * not written to {@code config/sanitypd_mental_hints.json}. The only effect is {@value
     * FORBIDDEN_NAME_PUNISHMENT} points of true damage on the sender, which is lethal for a player and
     * leaves the server itself completely untouched.
     *
     * <p>Early versions of this method also required {@code dist != CLIENT}. That was wrong for an
     * integrated server: it sent a player's own command back into the client branch, where the crash
     * helper shut the whole game down - including the world the command block belonged to.
     *
     * @param stack command source, used only to find the sender
     * @param text  the submitted hint text
     * @return {@code true} when the server half handled the command and the caller must return at once
     */
    private static boolean applyForbiddenNamePunishment(CommandSourceStack stack, String text)
    {
        // The detection rule lives in HiddenNameDetector, which deliberately carries no @OnlyIn and no
        // client types, so the server is free to call it. Never call HiddenNameWatcher from here: it is
        // @OnlyIn(CLIENT) and it halts the JVM.
        if (!piloser.sanitypd.client.HiddenNameDetector.mentionsTheName(text))
            return false;

        ServerPlayer speaker = (ServerPlayer) stack.getEntity();

        MinecraftServer server = stack.getServer();

        // A dedicated server must never halt, whatever the sender looks like: it would drop every other
        // player and lose the chunks it still holds in memory.
        if (server.isDedicatedServer())
            return true;

        // The host of a world that is open to the network is punished exactly like a guest, and for the
        // same reason: their game is the one holding the world, so halting it would take the server down
        // with it. "Published" is the signal the network menu sets when the world is shared, and unlike
        // the dist or the sender's address it distinguishes "playing alone" from "hosting".
        if (isSingleplayerOwnerOrUnshared(server, speaker))
            return false;

        // ---- A macaron buys the player out ----
        // Before the punishment lands: a player carrying macarons loses them and is left at one health
        // point instead of taking the forbidden-name damage. Checked here as well as in addHint's client
        // branch, so that every half answers the same way - a single command must have a single outcome.
        if (spareWithMacarons(speaker))
            return true;

        // Report the outcome, never the text. Without this line the punishment is invisible in the
        // server log, and a "did it fire?" question would need a live multiplayer session to answer.
        SanityMod.LOGGER.info("Hidden name punished on the server side: {} took {} points of true damage",
                speaker.getGameProfile().getName(), FORBIDDEN_NAME_PUNISHMENT);

        // Sourceless true damage, the same type the /sanity truedamage command uses: it ignores armour,
        // enchantments, resistance, absorption and shields, and its death message is the mod's own.
        SanityDamageTypes.dealTrueDamage(speaker, FORBIDDEN_NAME_PUNISHMENT, null);
        return true;
    }

    /**
     * Spares a player who is carrying macarons, at the cost of every macaron he has.
     *
     * <p>Speaking the name normally ends in either a crash or lethal damage. A player who happens to be
     * carrying macarons instead loses all of them - the main inventory and the offhand, which is where a
     * player would actually be carrying cake - and is left at exactly {@value #ESCAPE_HEALTH} health
     * point. The line about it is shown to that player alone.
     *
     * <p>Returns {@code false} for anything that is not a {@link ServerPlayer} with at least one macaron,
     * which is also what keeps a command block out: the mechanic is driven by the sender, and a command
     * block has no player behind it.
     *
     * @param sender whoever sent the command, possibly {@code null}
     * @return {@code true} when the escape was applied and the caller must not punish the sender
     */
    public static boolean spareWithMacarons(Entity sender)
    {
        if (!(sender instanceof ServerPlayer player))
            return false;

        if (!hasMacaron(player))
            return false;

        int taken = takeMacarons(player);

        // Exactly one health point, whatever the player had before: the brief is explicit that the health
        // is set rather than reduced. Health is clamped by the game, so this cannot exceed the maximum or
        // leave a dead player alive.
        player.setHealth(ESCAPE_HEALTH);

        SanityMod.LOGGER.info("[HIDDEN-NAME] {} paid {} macaron(s) for the name and was left at {} health",
                player.getGameProfile().getName(), taken, ESCAPE_HEALTH);

        // Chat, not the action bar: this is something the player noticed happening to him. Only the sender
        // receives it, so the other players learn nothing.
        player.sendSystemMessage(Component.translatable(ESCAPE_MESSAGE_KEY));

        return true;
    }

    /** Whether any macaron of any colour sits in the main inventory or the offhand. */
    private static boolean hasMacaron(ServerPlayer player)
    {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            if (isMacaron(player.getInventory().getItem(slot)))
                return true;
        }

        return false;
    }

    /**
     * Removes every macaron from the main inventory and the offhand.
     *
     * @return how many macarons were taken, for the log line
     */
    private static int takeMacarons(ServerPlayer player)
    {
        int taken = 0;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);

            if (!isMacaron(stack))
                continue;

            taken += stack.getCount();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }

        return taken;
    }

    /**
     * Whether that stack is one of this mod's macarons.
     *
     * <p>Checked against the registry list rather than a single item constant, so every colour counts -
     * and a colour added later counts without touching this method.
     */
    private static boolean isMacaron(ItemStack stack)
    {
        if (stack.isEmpty())
            return false;

        for (RegistryObject<Item> macaron : piloser.sanitypd.item.ItemRegistry.MACARONS())
        {
            if (macaron.isPresent() && stack.is(macaron.get()))
                return true;
        }

        return false;
    }

    /**
     * Whether this server belongs to a single player who is <b>not</b> sharing it.
     *
     * <p>That is the only situation where halting the JVM costs nothing extra: there is nobody else
     * connected and no world anyone else depends on. Once the world is opened to the network - or the
     * server is dedicated - the answer is {@code false} and the sender is punished instead of crashed,
     * even when the sender is the host himself.
     *
     * <p>{@code isPublished()} is what the "Open to LAN" flow sets; a dedicated server reports it too,
     * which is why the dedicated case is already handled before this method is reached.
     *
     * <p>WARNING: this is a secondary check. {@link #isRemoteServerPlayer} already answers "punish" for a
     * published world <b>without</b> looking at ownership, because a LAN host was once let through by an
     * ownership test that answered {@code false} for him. Do not make the routing depend on this method.
     */
    private static boolean isSingleplayerOwnerOrUnshared(MinecraftServer server, ServerPlayer speaker)
    {
        return !server.isPublished() && server.isSingleplayerOwner(speaker.getGameProfile());
    }

    /**
     * Whether the sender is a player who reached this game <b>over the network</b>.
     *
     * <p>This is what separates the two halves of the hidden-name easter egg: a player playing alone sees
     * the crash half, while everyone else - a guest on this machine's LAN world, a player on a dedicated
     * server, or the host once the world is shared - is refused silently and punished, because halting
     * their game would take a world other people depend on down with it.
     *
     * <h3>Why not compare the dist</h3>
     * An integrated server runs in the client process, so {@code dist} is {@code CLIENT} for single player
     * <b>and</b> for a LAN host <b>and</b> for every remote player who joins that LAN world. It cannot tell
     * them apart, and gating on it was the 1.2.1 bug.
     *
     * <h3>Why this cannot be the only test</h3>
     * The address alone cannot separate "playing alone" from "hosting": the host's own connection is
     * loopback as well. That case is settled by {@link #isSingleplayerOwnerOrUnshared} inside
     * {@link #applyForbiddenNamePunishment}; this method only covers the guests.
     *
     * <p>If the address cannot be read at all, the answer is {@code false} (treat the sender as the local
     * player). That keeps the crash half reachable in the environment this mod is actually played in -
     * a development or vanilla client with no address - and the routing log line in {@link #addHint}
     * prints the address, so a wrong guess is visible instead of silent.
     */
    private static boolean isRemoteServerPlayer(CommandSourceStack stack)
    {
        if (!(stack.getEntity() instanceof ServerPlayer speaker))
            return false;

        MinecraftServer server = stack.getServer();

        // A dedicated server has no local player at all: everyone who sends a command there is a guest,
        // and halting it would drop every one of them. Do not try to reason about ownership here - a
        // dedicated server reports itself as the singleplayer owner of its world, which is how an earlier
        // version let such a server fall through to the crash half.
        if (server.isDedicatedServer())
            return true;

        // A world that is open to the network is punished for everyone, the host included: his game is the
        // one holding the world, so halting it would take the server down with it. This test is about the
        // world, not about who is asking - relying on "is this the singleplayer owner" was not enough and
        // let a LAN host crash his own server.
        if (server.isPublished())
            return true;

        // Playing alone. The only question left is whether the sender is the player at this computer: the
        // integrated server gives him a non-network address (`local:E:<id>`), while a guest would have a
        // real, non-loopback one.
        return !isLocalConnection(speaker);
    }

    /**
     * Whether that player's connection comes from this very machine.
     *
     * <p>Handles both shapes a connection can take here: a real socket (a client on another machine, or
     * one on this machine connecting through the loopback interface) and a non-network address, which is
     * what the integrated server uses for the player sitting at this computer.
     */
    private static boolean isLocalConnection(ServerPlayer speaker)
    {
        try
        {
            java.net.SocketAddress address = speaker.connection.getRemoteAddress();

            if (!(address instanceof java.net.InetSocketAddress inet))
                return true;    // no network address at all: the player on this machine

            java.net.InetAddress host = inet.getAddress();

            if (host == null)
                return true;

            return host.isLoopbackAddress() || host.isAnyLocalAddress();
        }
        catch (Throwable t)
        {
            return true;
        }
    }

    private static int addHint(CommandSourceStack stack, SanityArgumentTypes.HintTier tier, String text)
    {
        final int index = tierIndex(tier);

        // Mentioning the name is what puts a command on the hidden-name track; everything else falls
        // through to the ordinary add path untouched.
        final boolean speaksTheName = piloser.sanitypd.client.HiddenNameDetector.mentionsTheName(text);

        // ---- Hidden content: the sender decides which half runs ----
        // Two halves, and only one of them ever runs:
        //   * the player who owns this game - the one playing on this machine - gets the crash half, so
        //     the outcome matches what a local player has always seen: the window simply disappears;
        //   * a player connected over the network (LAN or a dedicated server) gets the server half:
        //     silent cancel + lethal true damage, because their game is not the one holding the world;
        //   * anything with no player behind it - a command block, a command-block minecart, the server
        //     console - never reaches either half.
        // Whichever half runs, the player sees one outcome only, and the text is never stored.
        //
        // The two halves must stay disjoint: sending a crash over the network would kill every client,
        // and the documented failure mode of getting this wrong is a dedicated server that halts and
        // takes its unwritten chunks with it.
        //
        // Decide once, log it, then act on that single decision. Computing the verdict twice would let the
        // log and the behaviour drift apart, which is exactly the kind of "the log said X" trap this line
        // exists to remove.
        final boolean punishedServerSide = speaksTheName && isRemoteServerPlayer(stack);

        // The routing line below is the only way a player can tell which of those branches was taken:
        // "playing alone crashes, sharing punishes" is a difference nobody can read off a crash report, and
        // it was misdiagnosed twice by reasoning about the dist alone. It never prints the text.
        if (speaksTheName)
        {
            Entity sender = stack.getEntity();
            MinecraftServer routingServer = stack.getServer();
            SanityMod.LOGGER.info("[HIDDEN-NAME] command routed: side={} sender={} local={} peer={} published={} dedicated={} owner={} punished={} thread={}",
                    FMLEnvironment.dist,
                    sender == null ? "<none>" : sender.getClass().getSimpleName(),
                    sender instanceof ServerPlayer host && isLocalConnection(host),
                    sender instanceof ServerPlayer peer ? String.valueOf(peer.connection.getRemoteAddress()) : "-",
                    routingServer.isPublished(),
                    routingServer.isDedicatedServer(),
                    sender instanceof ServerPlayer owner
                            && routingServer.isSingleplayerOwner(owner.getGameProfile()),
                    punishedServerSide,
                    Thread.currentThread().getName());
        }

        // ---- The server half: silent refusal, plus lethal damage ----
        // Taken by whoever is not the sole player of an unshared world: a guest on a LAN world, anyone on a
        // dedicated server, and the host himself once the world is open to the network. Halting the game of
        // any of those would take a world other people depend on down with it.
        if (punishedServerSide)
        {
            applyForbiddenNamePunishment(stack, text);
            return 0;
        }

        // ---- Nobody to blame: a command block or the console mentioning the name ----
        // There is no player behind the command, so dispatching it to the client half would shut down the
        // game of whoever happens to be holding the mouse. Refuse it silently. Text that does not mention
        // the name is left completely alone, so a command block can still add ordinary lines in LAN.
        //
        // This is also why the macaron escape below can never fire from a command block: with no entity
        // there is no player to bribe, and the command is refused before any half runs.
        if (speaksTheName && stack.getEntity() == null)
            return 0;

        // ---- Hidden content: a macaron buys the player out ----
        // A player who is carrying macarons is spared: instead of the crash, every macaron is taken and
        // the player is left at exactly one health point. The message goes to the sender alone.
        if (speaksTheName && spareWithMacarons(stack.getEntity()))
            return 0;

        return runOnClient(stack, () ->
        {
            // ---- Hidden content: speaking the forbidden name is not allowed ----
            // A submitted text that mentions the hidden name (allowing any spacing or punctuation between
            // the letters) closes the game on the spot and leaves the watcher message in the crash report.
            // Deliberately placed before all validation: mentioning the name is enough, even a duplicate or
            // over-long line triggers it.
            // The catch below covers the case where the easter egg itself fails (for example halt() being
            // unavailable in some environment); this try must never swallow the crash and silently disable
            // the hidden content, so failures are logged at ERROR level.
            try
            {
                piloser.sanitypd.client.HiddenNameWatcher.mentionCheck(text);
            }
            catch (Throwable t)
            {
                piloser.sanitypd.client.HiddenNameWatcher.logFallback(t);
            }

            piloser.sanitypd.client.MentalHintManager.AddResult result =
                    piloser.sanitypd.client.MentalHintManager.addHint(index, text);

            // Deliberately silent refusal: the text repeats a built-in line of the extra inner-voice pool, so
            // the command reports neither success nor failure and the line never enters the pool.
            if (result.silent())
                return ClientResult.ok(0);

            if (result.errorKey() != null)
                return ClientResult.error(result.errorKey());

            stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.add.success",
                    tierName(tier), text), true);
            return ClientResult.ok(1);
        });
    }

    private static int removeHint(CommandSourceStack stack, SanityArgumentTypes.HintTier tier, int displayIndex)
    {
        final int index = tierIndex(tier);

        return runOnClient(stack, () ->
        {
            String removed = piloser.sanitypd.client.MentalHintManager.removeHint(index, displayIndex);

            if (removed == null)
                return ClientResult.error("commands.sanity.hint.remove.invalid");

            stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.remove.success",
                    tierName(tier), removed), true);
            return ClientResult.ok(1);
        });
    }

    private static int clearHints(CommandSourceStack stack, SanityArgumentTypes.HintTier tier)
    {
        final int index = tierIndex(tier);

        return runOnClient(stack, () ->
        {
            int cleared = piloser.sanitypd.client.MentalHintManager.clearHints(index);
            stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.clear.success",
                    tierName(tier), cleared), true);
            return ClientResult.ok(cleared);
        });
    }

    /**
     * Lists every candidate line of a tier.
     *
     * <p>Indexing: <b>1..number of built-in lines</b> are the built-in ones (which cannot be removed),
     * followed by the custom ones, so the index the player sees is always the index {@code remove} takes.
     *
     * <p>Note: the candidate pool now mixes built-in and custom entries, so the built-in / custom tag must
     * come from <b>each entry's own source</b> and must not be guessed from "index &gt; built-in count",
     * which would mislabel entries.
     */
    private static int listHints(CommandSourceStack stack, SanityArgumentTypes.HintTier tier)
    {
        final int index = tierIndex(tier);

        return runOnClient(stack, () ->
        {
            final int builtinCount = piloser.sanitypd.client.MentalHintManager.defaultCount(index);
            final List<piloser.sanitypd.client.MentalHintManager.Hint> pool =
                    piloser.sanitypd.client.MentalHintManager.effectiveHintEntries(index);
            final int customCount = piloser.sanitypd.client.MentalHintManager.customCount(index);

            stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.list.header",
                    tierName(tier), pool.size(), customCount), false);

            for (int i = 0; i < pool.size(); i++)
            {
                final int number = i + 1;
                final String text = pool.get(i).text();
                final boolean isCustom = pool.get(i).custom();

                stack.sendSuccess(() -> Component.translatable(
                        isCustom ? "commands.sanity.hint.list.custom" : "commands.sanity.hint.list.builtin",
                        number, text), false);
            }

            if (customCount == 0)
                stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.list.using_builtin", tierName(tier)), false);
            else
                // The "mixed" lang entry has 3 placeholders (tier name / built-in count / custom count),
                // so exactly 3 arguments must be passed in that order.
                // One argument short makes String.format inside TranslatableContents#getString throw
                // IllegalFormatException / MissingFormatArgumentException, and the whole line is then shown
                // verbatim instead of being translated.
                // For comparison, list.header in this same method also has 3 placeholders and passes
                // tierName, pool.size(), customCount, so its order is correct and it always renders.
                stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.list.mixed",
                        tierName(tier), builtinCount, customCount), false);

            return ClientResult.ok(Math.max(1, pool.size()));
        });
    }

    private static int showHint(CommandSourceStack stack, SanityArgumentTypes.HintTier tier)
    {
        final int index = tierIndex(tier);

        return runOnClient(stack, () ->
        {
            boolean shown = piloser.sanitypd.client.MentalHintManager.showNow(index);

            if (!shown)
                return ClientResult.error("commands.sanity.hint.show.empty");

            stack.sendSuccess(() -> Component.translatable("commands.sanity.hint.show.success", tierName(tier)), true);
            return ClientResult.ok(1);
        });
    }

    /** Display name of a tier (mild / severe / deep). */
    private static Component tierName(SanityArgumentTypes.HintTier tier)
    {
        return Component.translatable(tier.translationKey());
    }

    /**
     * {@code /sanity nospawn on|off}: <b>testing aid</b> that <b>clears and blocks</b> every
     * "non-inner" monster while active (slimes, phantoms, magma cubes and so on).
     *
     * <p>Filtering uses the {@code MobCategory.MONSTER} category, so a slime, which is not a
     * {@code Monster} but is categorised as MONSTER, is caught too; inner entities
     * (crawler / lurker / stalker) are always allowed through. See {@code event/TestSpawnGuard}.
     *
     * <p><b>Bosses are exempt</b> (Ender Dragon, Wither, Warden, Elder Guardian): the dragon and the
     * wither are {@code MONSTER}-category but one-per-world, so clearing them would leave the save
     * uncompletable. See {@code TestSpawnGuard#isProtectedBoss}.
     */
    private static int setNoSpawn(CommandSourceStack stack, boolean enabled)
    {
        int removed = TestSpawnGuard.setEnabled(stack.getServer(), enabled);

        if (enabled)
            stack.sendSuccess(() -> Component.translatable("commands.sanity.nospawn.on", removed), true);
        else
            stack.sendSuccess(() -> Component.translatable("commands.sanity.nospawn.off"), true);

        return 1;
    }

    private static int noSpawnStatus(CommandSourceStack stack)
    {
        boolean enabled = TestSpawnGuard.isEnabled();

        stack.sendSuccess(() -> Component.translatable(enabled
                ? "commands.sanity.nospawn.status.on"
                : "commands.sanity.nospawn.status.off"), false);

        return enabled ? 1 : 0;
    }

    /**
     * {@code /sanity innerspawn on|off}: <b>testing aid</b> that <b>clears and blocks</b> inner entities
     * while active (decayed stalker / dread lurker / shrieking crawler, plus any entity a datapack adds to
     * the {@code sanitypd:inner_entities} tag).
     *
     * <p>It is a <b>separate switch</b> from {@code /sanity nospawn}; enabling both means no mobs at all.
     * See {@code event/TestSpawnGuard}.
     */
    private static int setInnerNoSpawn(CommandSourceStack stack, boolean enabled)
    {
        int removed = TestSpawnGuard.setInnerBlockEnabled(stack.getServer(), enabled);

        if (enabled)
            stack.sendSuccess(() -> Component.translatable("commands.sanity.innerspawn.on", removed), true);
        else
            stack.sendSuccess(() -> Component.translatable("commands.sanity.innerspawn.off"), true);

        return 1;
    }

    private static int innerNoSpawnStatus(CommandSourceStack stack)
    {
        boolean enabled = TestSpawnGuard.isInnerBlockEnabled();

        stack.sendSuccess(() -> Component.translatable(enabled
                ? "commands.sanity.innerspawn.status.on"
                : "commands.sanity.innerspawn.status.off"), false);

        return enabled ? 1 : 0;
    }

    /**
     * /sanity set: sets sanity points directly.
     * The target is not limited to players; any mob with the sanity capability works (consistent with
     * {@code get} and {@code state}), so debugging mob sanity needs no other command.
     */
    private static int setSanity(CommandSourceStack stack, Collection<? extends Entity> targets, float value)
    {
        int count = 0;
        Entity last = null;

        for (Entity entity : targets)
        {
            if (!(entity instanceof LivingEntity living))
                continue;

            ISanity cap = living.getCapability(SanityProvider.CAP).orElse(null);
            if (cap == null)
                continue;

            cap.setSanity(value); // value is in sanity points: 100 = sane, 0 = insane
            last = entity;
            count++;
        }

        if (count == 0)
        {
            stack.sendFailure(Component.translatable("commands.sanity.no_sanity"));
            return 0;
        }

        final Entity shown = last;
        final int total = count;
        if (count == 1)
            stack.sendSuccess(() -> Component.translatable("commands.sanity.set.success.single", shown.getDisplayName(), value), true);
        else
            stack.sendSuccess(() -> Component.translatable("commands.sanity.set.success.multiple", value, total), true);

        return count;
    }

    /** /sanity add: adds or subtracts points on top of the current sanity (any mob with sanity works too). */
    private static int addSanity(CommandSourceStack stack, Collection<? extends Entity> targets, float value)
    {
        int count = 0;
        Entity last = null;

        for (Entity entity : targets)
        {
            if (!(entity instanceof LivingEntity living))
                continue;

            ISanity cap = living.getCapability(SanityProvider.CAP).orElse(null);
            if (cap == null)
                continue;

            cap.setSanity(cap.getSanity() + value); // add = straight addition / subtraction of sanity points
            last = entity;
            count++;
        }

        if (count == 0)
        {
            stack.sendFailure(Component.translatable("commands.sanity.no_sanity"));
            return 0;
        }

        final Entity shown = last;
        final int total = count;
        if (count == 1)
            stack.sendSuccess(() -> Component.translatable("commands.sanity.add.success.single", value, shown.getDisplayName()), true);
        else
            stack.sendSuccess(() -> Component.translatable("commands.sanity.add.success.multiple", total, value), true);

        return count;
    }

    /** Debug: print the target's sanity points. Entities without sanity (inner entities) get a notice. */
    private static int getSanity(CommandSourceStack stack, Collection<? extends Entity> targets)
    {
        int found = 0;
        boolean unsupported = false;
        for (Entity entity : targets)
        {
            ISanity cap = entity.getCapability(SanityProvider.CAP).orElse(null);
            if (cap == null)
            {
                unsupported = true;
                continue;
            }
            final Entity shown = entity;
            final float sanity = cap.getSanity();
            final float max = cap.getMaxSanity();
            stack.sendSuccess(() -> Component.translatable("commands.sanity.get.success",
                    shown.getDisplayName(), format(sanity), format(max)), false);
            found++;
        }
        if (found == 0 && unsupported)
            stack.sendFailure(Component.translatable("commands.sanity.no_sanity"));
        return found;
    }

    /** Debug: show the target's sanity plus its confusion / mania state (timers are shown in seconds). */
    private static int getState(CommandSourceStack stack, Collection<? extends Entity> targets)
    {
        int found = 0;
        boolean unsupported = false;
        for (Entity entity : targets)
        {
            ISanity cap = entity.getCapability(SanityProvider.CAP).orElse(null);
            if (cap == null)
            {
                unsupported = true;
                continue;
            }
            final Entity shown = entity;
            final float sanity = cap.getSanity();
            final float max = cap.getMaxSanity();
            final float low = cap.getLowSanityTicks() / 20.0f;
            final float confusion = cap.getConfusionTicks() / 20.0f;
            final float mania = cap.getManiaTicks() / 20.0f;
            final Component stateName = Component.translatable(stateKey(cap));
            stack.sendSuccess(() -> Component.translatable("commands.sanity.state.success",
                    shown.getDisplayName(), format(sanity), format(max), stateName,
                    format(low), format(confusion), format(mania)), false);
            found++;
        }
        if (found == 0 && unsupported)
            stack.sendFailure(Component.translatable("commands.sanity.no_sanity"));
        return found;
    }

    /** Which phase the target is currently in. */
    private static String stateKey(ISanity cap)
    {
        if (cap.getManiaTicks() > 0)
            return "commands.sanity.state.mania";
        if (cap.getConfusionTicks() > 0)
            return "commands.sanity.state.confusion";
        if (cap.getLowSanityTicks() > 0)
            return "commands.sanity.state.low";
        return "commands.sanity.state.normal";
    }

    /** Debug: deal psychic damage (which automatically goes "drain sanity -> convert the overflow to true damage"). */
    private static int applyPsychic(CommandSourceStack stack, Collection<? extends Entity> targets, float amount)
    {
        Entity source = stack.getEntity();
        int applied = 0;
        for (Entity entity : targets)
        {
            if (entity instanceof LivingEntity living && living.isAlive())
            {
                SanityDamageTypes.dealPsychic(living, amount, source);
                applied++;
            }
        }
        final int count = applied;
        stack.sendSuccess(() -> Component.translatable("commands.sanity.psychic.success", format(amount), count), true);
        return count;
    }

    /** Debug: deal true damage (ignores armour / enchantments / resistance / absorption / shields). */
    private static int applyTrueDamage(CommandSourceStack stack, Collection<? extends Entity> targets, float amount)
    {
        // Deliberately does NOT treat the executor as the attacker: the intent is sourceless true damage.
        // The command is not an in-game attacker either, and passing null is what reaches the
        // "no source" branch of dealTrueDamage.
        int applied = 0;
        for (Entity entity : targets)
        {
            if (entity instanceof LivingEntity living && living.isAlive())
            {
                SanityDamageTypes.dealTrueDamage(living, amount, null);
                applied++;
            }
        }
        final int count = applied;
        stack.sendSuccess(() -> Component.translatable("commands.sanity.truedamage.success", format(amount), count), true);
        return count;
    }

    private static int reloadConfig(CommandSourceStack stack)
    {
        DimensionConfig.init();
        stack.sendSuccess(() -> Component.translatable("commands.sanity.config.reload"), true);
        return 1;
    }

    /** Integers without a decimal, otherwise one decimal place. */
    private static String format(float value)
    {
        return Math.abs(value - Math.round(value)) < 0.05f
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
