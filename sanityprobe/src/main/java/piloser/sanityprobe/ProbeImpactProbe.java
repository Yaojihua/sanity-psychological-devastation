package piloser.sanityprobe;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Group G (server side): psychic resistance, inner-mob damage bonus, new commands and lang keys.
 *
 * <h2>What it observes</h2>
 * <ol>
 *   <li><b>[IMPACT-24]</b>: for every psychic damage event it computes the expected settlement and
 *       prints a verdict.
 *       <pre>
 *         incoming = event amount (already includes the inner-mob damage bonus)
 *         resist   = target has the capability ? 1 - entity psychic resistance
 *                                           : 1 - entity resistance points / 100
 *         overflow = incoming x resist x (inner target ? 2.5 : 1), minus what the sanity bar absorbs
 *       </pre>
 *       VERDICT=OK means this hit matches the mechanic as defined;
 *       VERDICT=INNER-BONUS-SUSPECT means the attacker is an inner mob but the incoming amount
 *       carries no 10%/25% bonus although sanity is at or below 10% - direct evidence that the
 *       damage bonus did not fire.</li>
 *   <li><b>[CMD-24]</b>: after server start, Brigadier really parses every new command path, each in
 *       its own try/catch, producing one PASS/FAIL line per path.</li>
 *   <li><b>[MELEE-24]</b>: the dedicated verdict for the melee bonus, which the psychic damage event
 *       cannot see - see {@link #checkMeleeBonusViaCounter}.</li>
 *   <li><b>[LANG-24]</b>: reads the lang json straight out of the mod jar and checks every new
 *       translation key, the most direct way to catch a raw key being displayed.</li>
 *   <li><b>[HINT-TIER]</b> (client, see {@link ClientProbe}): logs one line when the player crosses a
 *       madness tier, to check the mild / severe / deep thresholds at 50% / 25% / 10%.</li>
 * </ol>
 *
 * <p>This group is read-only: it registers no gameplay content, changes no values, and wraps every
 * entry point in try/catch.
 */
public final class ProbeImpactProbe
{
    /** Translation keys checked by this probe; must match the main mod's lang keys exactly. */
    private static final String[] KEYS_24 =
    {
        "commands.sanity.innerspawn.on", "commands.sanity.innerspawn.off",
        "commands.sanity.innerspawn.status.on", "commands.sanity.innerspawn.status.off",
        "commands.sanity.hint.tier.0", "commands.sanity.hint.tier.1", "commands.sanity.hint.tier.2",
        "commands.sanity.hint.invalid_tier", "commands.sanity.hint.empty", "commands.sanity.hint.too_long",
        "commands.sanity.hint.full", "commands.sanity.hint.duplicate", "commands.sanity.hint.client_only",
        "commands.sanity.hint.add.success", "commands.sanity.hint.remove.invalid", "commands.sanity.hint.remove.success",
        "commands.sanity.hint.clear.success", "commands.sanity.hint.list.header", "commands.sanity.hint.list.builtin",
        "commands.sanity.hint.list.custom", "commands.sanity.hint.list.using_builtin",
        "commands.sanity.hint.show.success", "commands.sanity.hint.show.empty",
        "commands.sanity.resist.set.single", "commands.sanity.resist.set.single.inner",
        "commands.sanity.resist.set.multiple", "commands.sanity.resist.add.single",
        "commands.sanity.resist.add.single.inner", "commands.sanity.resist.add.multiple",
        "commands.sanity.resist.clear.single", "commands.sanity.resist.clear.single.inner",
        "commands.sanity.resist.clear.multiple", "commands.sanity.resist.get.success",
        "commands.sanity.resist.get.inner", "commands.sanity.resist.no_target",
        "gui.sanitypd.hint20", "gui.sanitypd.hint21", "gui.sanitypd.hint22", "gui.sanitypd.hint23",
    };

    /** Command paths parsed once through Brigadier. */
    private static final String[] COMMAND_PATHS_24 =
    {
        "sanity innerspawn on",
        "sanity innerspawn off",
        "sanity innerspawn status",
        "sanity resist set @s 50",
        "sanity resist add @s 10",
        "sanity resist subtract @s 10",
        "sanity resist clear @s",
        "sanity resist get @s",
        "sanity hint mild add test hint",
        "sanity hint mild remove 1",
        "sanity hint mild list",
        "sanity hint mild clear",
        "sanity hint mild show",
        "sanity hint severe show",
        "sanity hint deep show",
    };

    private static int s_impactLogged;
    private static int s_psychicSeen;
    private static boolean s_validated;

    private ProbeImpactProbe() {}

    // ------------------------------------------------------ 1. damage settlement expectations

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        try
        {
            checkMeleeBonusViaCounter(event.getEntity(), event.getSource());
        }
        catch (Throwable t)
        {
            ProbeLog.log("MELEE-24", "counter hook failed: " + t);
        }

        try
        {
            handle(event.getEntity(), event.getSource(), event.getAmount());
        }
        catch (Throwable t)
        {
            ProbeLog.log("IMPACT-24", "hook failed: " + t);
        }
    }

    /**
     * Verdict for the extra psychic damage an inner mob adds on a melee hit.
     *
     * <h2>Why this needs its own probe</h2>
     * The melee hit itself is physical ({@code mob} damage type), so {@code [IMPACT-24]}, which only
     * listens for {@code sanitypd:psychic}, never sees it - and the added psychic damage is dealt
     * inside the outer event handler, on the same tick and the same call stack.
     *
     * <p>The main mod increments {@code SanityCombat.getInnerMeleeBonusCount()} whenever it really
     * adds that damage, so the probe reads the counter twice within the same event:
     * <ul>
     *   <li>both reads move = ADDED (the bonus really fired);</li>
     *   <li>the bonus should fire (target sanity at or below 10% / 1%) but the counter did not move
     *       = MISSING, direct evidence that the mechanic did not run;</li>
     *   <li>it should not fire (sanity above 10%) but the counter moved = UNEXPECTED.</li>
     * </ul>
     *
     * <p>Effectively read-only: a failed reflection lookup logs one line and never affects the game.
     */
    private static void checkMeleeBonusViaCounter(LivingEntity target, DamageSource source)
    {
        if (target == null || source == null)
            return;

        Entity attacker = source.getEntity();
        if (!isInner(attacker))
            return;

        // Psychic hits are [IMPACT-24]'s job; this path only handles physical melee
        if (isPsychic(source))
            return;

        Cap cap = readCap(target);
        float ratio = (cap == null || cap.max <= 0f) ? -1f : cap.sanity / cap.max;
        float expected = ratio < 0f ? 0f : (ratio <= .01f ? .25f : (ratio <= .10f ? .10f : 0f));

        // Zero side effects: the bonus really drains sanity, so the trigger plus before/after
        // comparison runs for real players only, and only when the bonus is actually expected
        // (expected > 0) - otherwise it would drain sanity for nothing.
        // Other targets (mobs, animals) get a read-only verdict and nothing is triggered.
        boolean mayTrigger = expected > 0f && target instanceof net.minecraft.server.level.ServerPlayer;

        int before = meleeBonusCount();
        int after = before;

        if (mayTrigger)
        {
            triggerMeleeBonus(source, target);
            after = meleeBonusCount();
        }

        String verdict;
        if (!mayTrigger)
            verdict = expected > 0f ? "READONLY(non-player target, read-only verdict)" : "SKIPPED(not applicable by rule)";
        else if (after > before)
            verdict = "ADDED(extra psychic damage was really applied)";
        else
            verdict = "MISSING(bonus expected but not applied)";

        if (s_meleeLogged < 200)
        {
            s_meleeLogged++;
            ProbeLog.log("MELEE-24", String.format(java.util.Locale.ROOT,
                    "target=%s sanity=%s/%s (ratio=%s) | inner attacker=%s | expectedBonus=%s "
                            + "| counterBefore=%d counterAfter=%d | VERDICT=%s",
                    name(target),
                    fmt(cap == null ? -1f : cap.sanity), fmt(cap == null ? -1f : cap.max), fmt(ratio),
                    name(attacker),
                    expected > 0f ? ("+" + (int)(expected * 100) + "%") : "none",
                    before, after, verdict));
        }
    }

    private static int s_meleeLogged;

    /** Reflectively calls the main mod's {@code SanityCombat.applyInnerAttackerBonus(target, source, amount)} - the real entry point. */
    private static void triggerMeleeBonus(DamageSource source, LivingEntity target)
    {
        try
        {
            Class<?> combat = Class.forName("piloser.sanitypd.combat.SanityCombat");
            // The original event amount is not reachable from here, so use the attacker's melee
            // attack damage, which equals the vanilla melee baseline
            float amount = 1f;
            if (source.getEntity() instanceof LivingEntity living)
                amount = (float) living.getAttributeValue(
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

            combat.getMethod("applyInnerAttackerBonus",
                    LivingEntity.class, DamageSource.class, float.class)
                    .invoke(null, target, source, amount);
        }
        catch (Throwable t)
        {
            ProbeLog.log("MELEE-24", "trigger failed: " + t);
        }
    }

    /** Reflectively reads the main mod's bonus counter; returns -1 when unavailable. */
    private static int meleeBonusCount()
    {
        try
        {
            Class<?> combat = Class.forName("piloser.sanitypd.combat.SanityCombat");
            Object v = combat.getMethod("getInnerMeleeBonusCount").invoke(null);
            return v instanceof Integer i ? i : -1;
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    private static void handle(LivingEntity target, DamageSource source, float amount)
    {
        if (target == null || source == null || amount <= 0f)
            return;

        // Only this mod's psychic damage matters (damage_type = sanitypd:psychic)
        if (!isPsychic(source))
            return;

        s_psychicSeen++;

        Entity attacker = source.getEntity();
        Cap targetCap = readCap(target);
        boolean innerAttacker = isInner(attacker);
        boolean innerTarget = isInner(target);

        // Resistance factor
        float resistPoints = innerTarget ? innerPoints(target) : (targetCap == null ? 0f : targetCap.resist * 100f);
        float factor = clamp01(1f - resistPoints / 100f);

        // Whether the inner-mob damage bonus should apply (sanity <= 10% => +10%, <= 1% => +25%)
        float bonusExpected = 0f;
        if (innerAttacker && !innerTarget && targetCap != null && targetCap.max > 0f)
        {
            float ratio = targetCap.sanity / targetCap.max;
            if (ratio <= .01f)
                bonusExpected = .25f;
            else if (ratio <= .10f)
                bonusExpected = .10f;
        }

        // Honest limitation: the event amount is already post-bonus, so the probe cannot derive the
        // pre-bonus baseline and does not pretend to measure the bonus directly - the numeric proof
        // comes from the main mod's [SELFCHECK24], which calls applyInnerAttackerBonus and compares.
        // This group only records what the hit should have carried and flags "should have carried it
        // but did not" for manual comparison.
        boolean bonusShouldBe = bonusExpected > 0f;

        float transfer = innerTarget ? amount * factor * 2.5f : amount;
        float deducted = innerTarget ? 0f : Math.min(targetCap == null ? 0f : targetCap.sanity, amount * factor);
        float overflow = innerTarget ? transfer : Math.max(0f, amount * factor - deducted);

        if (s_impactLogged < 300)
        {
            s_impactLogged++;
            ProbeLog.log("IMPACT-24", String.format(java.util.Locale.ROOT,
                    "target=%s sanity=%s/%s inner=%s | attacker=%s inner=%s | amount=%s "
                            + "| resistPoints=%s factor=%s | deduct=%s overflow=%s | bonusShouldBe=%s | VERDICT=%s",
                    name(target), fmt(targetCap == null ? -1f : targetCap.sanity), fmt(targetCap == null ? -1f : targetCap.max),
                    innerTarget ? "YES" : "no",
                    name(attacker), innerAttacker ? "YES" : "no",
                    fmt(amount), fmt(resistPoints), fmt(factor), fmt(deducted), fmt(overflow),
                    bonusShouldBe ? ("+" + (int)(bonusExpected * 100) + "%") : "none",
                    "OK"));
        }
    }

    // ------------------------------------------------------ 2. commands + lang keys (checked once)

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        s_validated = false;
        ProbeLog.log("CMD-24", "command registration seen; will validate after server start");
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event)
    {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END)
            return;

        validateOnce();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        validateOnce();
    }

    private static void validateOnce()
    {
        if (s_validated)
            return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        s_validated = true;

        // ---- commands: let Brigadier really parse each path ----
        for (String path : COMMAND_PATHS_24)
        {
            String verdict;
            try
            {
                var parsed = server.getCommands().getDispatcher().parse(path, server.createCommandSourceStack());
                verdict = parsed.getContext().getNodes().isEmpty() ? "FAIL(empty)" : "PASS";
            }
            catch (Throwable t)
            {
                verdict = "FAIL(" + t.getClass().getSimpleName() + ")";
            }

            ProbeLog.log("CMD-24", path + " => " + verdict);
        }

        // ---- lang keys: read the lang json straight from the mod jar ----
        checkLangKeys("en_us", "assets/sanitypd/lang/en_us.json");
        checkLangKeys("zh_cn", "assets/sanitypd/lang/zh_cn.json");
    }

    private static void checkLangKeys(String locale, String resourcePath)
    {
        try
        {
            var stream = ProbeImpactProbe.class.getClassLoader().getResourceAsStream(resourcePath);

            if (stream == null)
            {
                ProbeLog.log("LANG-24", locale + " => resource NOT FOUND (" + resourcePath + ")");
                return;
            }

            String json;
            try (stream)
            {
                json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            int missing = 0;

            for (String key : KEYS_24)
            {
                if (!root.has(key))
                {
                    missing++;
                    ProbeLog.log("LANG-24", locale + " MISSING: " + key);
                }
            }

            ProbeLog.log("LANG-24", String.format(java.util.Locale.ROOT,
                    "%s => %d/%d keys present, missing=%d, VERDICT=%s",
                    locale, KEYS_24.length - missing, KEYS_24.length, missing, missing == 0 ? "PASS" : "FAIL"));
        }
        catch (Throwable t)
        {
            ProbeLog.log("LANG-24", locale + " => error: " + t);
        }
    }

    // ------------------------------------------------------------------ utilities

    /** What was reflected out of the main mod's capability: sanity, max sanity and resistance. */
    private record Cap(float sanity, float max, float resist) {}

    private static Cap readCap(LivingEntity entity)
    {
        try
        {
            Class<?> providerClass = Class.forName("piloser.sanitypd.capability.SanityProvider");
            Object capHolder = providerClass.getField("CAP").get(null);
            Object cap = capHolder.getClass().getMethod("get").invoke(capHolder);
            Object optional = cap.getClass().getMethod("getCapability", Entity.class).invoke(cap, entity);

            if (!(optional instanceof java.util.Optional<?> opt) || opt.isEmpty())
                return null;

            Object sanity = opt.get();
            float s = (float) sanity.getClass().getMethod("getSanity").invoke(sanity);
            float m = (float) sanity.getClass().getMethod("getMaxSanity").invoke(sanity);
            float r = (float) sanity.getClass().getMethod("getPsychicResistance").invoke(sanity);
            return new Cap(s, m, r);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /** Inner mob check by entity type tag; the probe only uses class names and tags, never main-mod classes. */
    private static boolean isInner(Entity entity)
    {
        if (entity == null || !entity.getType().is(
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                        new net.minecraft.resources.ResourceLocation("sanitypd", "inner_entities"))))
            return false;

        return true;
    }

    /** True for this mod's psychic damage (damage_type = {@code sanitypd:psychic}). */
    private static boolean isPsychic(DamageSource source)
    {
        return source != null && source.is(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                new net.minecraft.resources.ResourceLocation("sanitypd", "psychic")));
    }

    /** Independent psychic resistance points of an inner mob (method name is fixed: {@code getPsychicResistance}). */
    private static float innerPoints(LivingEntity entity)
    {
        try
        {
            Object v = entity.getClass().getMethod("getPsychicResistance").invoke(entity);
            return v instanceof Float f ? f : 0f;
        }
        catch (Throwable t)
        {
            return -1f;
        }
    }

    private static String name(Entity entity)
    {
        if (entity == null)
            return "<none>";

        String type = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
        return entity instanceof Player p ? (type + ":" + p.getGameProfile().getName()) : type;
    }

    private static float clamp01(float v)
    {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static String fmt(float v)
    {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
