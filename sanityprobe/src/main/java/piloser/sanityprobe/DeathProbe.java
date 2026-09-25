package piloser.sanityprobe;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Group A: death messages / damage chain.
 *
 * <p>Players only ({@link ServerPlayer}); otherwise mobs would flood the log. The three events
 * cover the pipeline in dispatch order: {@code LivingHurtEvent} (raw amount) →
 * {@code LivingDamageEvent} (final amount) → {@code LivingDeathEvent} (death resolution).
 *
 * <p>Together with the two mixins (call stacks of {@code sendSystemMessage} and
 * {@code ServerPlayer.die}) this answers why a single death can broadcast two different
 * messages, and who sent the second one.
 */
public final class DeathProbe
{
    private DeathProbe() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        ProbeLog.log("DEATH", "HURT   " + describe(event.getSource())
                + " amount=" + ProbeLog.fmt(event.getAmount())
                + " hp=" + ProbeLog.fmt(player.getHealth())
                + " dead=" + player.isDeadOrDying());

        // Also check whether INNER_IMMUNITY was breached.
        checkInnerImmunityBreach(player, event.getSource(), event.getAmount());
    }

    /**
     * Detects a breach of the INNER_IMMUNITY effect.
     *
     * <p>Retaliation must be told apart from a real breach. The effect only stops inner mobs
     * from attacking the holder on their own: once the holder attacks first, the mob is
     * supposed to fight back and deal damage, so "buffed player takes a hit from an inner mob"
     * is not a bug by itself.
     *
     * <p>Reporting every such hit drowns the real signal in normal retaliations. The routing
     * below therefore compares the victim with the attacker's {@code getLastHurtByMob()}:
     * a hit from a mob this player just attacked is logged as expected, and only a hit from a
     * mob the player never touched is treated as a breach and logged with a stack trace.
     */
    static void checkInnerImmunityBreach(ServerPlayer player, DamageSource source, float amount)
    {
        try
        {
            net.minecraft.world.effect.MobEffect immunity = SanityReflect.effect("INNER_IMMUNITY");

            if (immunity == null || !player.hasEffect(immunity))
                return;

            net.minecraft.world.entity.Entity attacker = source.getEntity();

            if (!isInnerEntity(attacker))
                return;

            // Did this player just hit it? Then the damage is expected retaliation.
            boolean retaliation = attacker instanceof net.minecraft.world.entity.Mob mob
                    && mob.getLastHurtByMob() == player;

            String who = attacker.getType() + "#" + attacker.getId();

            if (retaliation)
            {
                ProbeLog.log("IMMUNE-24",
                        "player with INNER_IMMUNITY " + player.getGameProfile().getName() + " was hit by " + who
                                + " for " + ProbeLog.fmt(amount) + " (msgId=" + ProbeLog.safe(source::getMsgId)
                                + ") | VERDICT=EXPECTED(retaliation - the player attacked first, by design)");
                return;
            }

            ProbeLog.logStack("IMMUNE-BREACH",
                    "★ player has INNER_IMMUNITY and **never attacked it**, yet was hit by " + who
                            + " for " + ProbeLog.fmt(amount) + " (msgId=" + ProbeLog.safe(source::getMsgId)
                            + ") - immunity did not hold!", 8);
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    /** True for the main mod's inner mobs, matched by registry name (no compile-time dependency). */
    private static boolean isInnerEntity(net.minecraft.world.entity.Entity entity)
    {
        if (entity == null)
            return false;

        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        if (key == null || !"sanitypd".equals(key.getNamespace()))
            return false;

        return key.getPath().equals("screaming_crawler")
                || key.getPath().equals("rotting_stalker")
                || key.getPath().equals("sneaking_terror");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        ProbeLog.log("DEATH", "DAMAGE " + describe(event.getSource())
                + " amount=" + ProbeLog.fmt(event.getAmount())
                + " hp=" + ProbeLog.fmt(player.getHealth())
                + " dead=" + player.isDeadOrDying());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        DamageSource source = event.getSource();

        ProbeLog.logStack("DEATH", "DIED   " + describe(source)
                + " hp=" + ProbeLog.fmt(player.getHealth())
                + " | localizedDeathMessage='" + ProbeLog.safe(() -> source.getLocalizedDeathMessage(player).getString()) + "'"
                + " | combatTracker='" + ProbeLog.safe(() -> player.getCombatTracker().getDeathMessage().getString()) + "'"
                + " | combatEntries=" + ProbeLog.safe(() -> String.valueOf(player.getCombatTracker().getCombatDuration()))
                + " | deathTime=" + player.deathTime
                + " | sanitypdPresent=" + hasSanityMod(), 12);

        // Raw (untranslated) death-message key check.
        checkRawDeathKey(player, source);

        // Wrong death-message flavour check (see below).
        checkDeathFlavour(player, source);
    }

    /**
     * Detects a death message that uses the wrong flavour.
     *
     * <p>Both directions are watched:
     * <ol>
     *   <li><b>Wrong flavour shown</b>: the damage source has an attacker (the player really was
     *       killed by an entity) ⇒ the message must never be the one reserved for sourceless
     *       true damage ({@code ......<player>?});</li>
     *   <li><b>Missing flavour</b>: {@code msgId == true_damage} (sourceless true damage, e.g.
     *       self-inflicted via a command) ⇒ the message must be that one.</li>
     * </ol>
     *
     * <p>The test does not depend on lang text: it only checks whether the resolved message
     * starts with {@code ......}.
     */
    static void checkDeathFlavour(ServerPlayer player, DamageSource source)
    {
        try
        {
            String localized = source.getLocalizedDeathMessage(player).getString();
            boolean flavour = localized != null && localized.startsWith("......");

            net.minecraft.world.entity.Entity attacker = source.getEntity();
            boolean hasAttacker = attacker != null && attacker != player;
            String msgId = ProbeLog.safe(source::getMsgId);
            boolean sourcelessTrueDamage = "true_damage".equals(msgId);

            if (hasAttacker && flavour)
            {
                ProbeLog.logStack("DEATH-FLAVOUR",
                        "★ wrong death-message flavour! this death **has an attacker** (" + attacker.getType() + "#" + attacker.getId()
                                + ", msgId=" + msgId + ") but shows the message reserved for 'sourceless true damage' localized='"
                                + localized + "' => check the true-damage type routing in SanityDamageTypes"
                                + " (an attributed death must use true_damage_attributed)", 8);
            }
            else if (sourcelessTrueDamage && !flavour)
            {
                ProbeLog.logStack("DEATH-FLAVOUR",
                        "★ the sourceless true-damage message (msgId=true_damage) is **missing**: localized='" + localized
                                + "' => in lang both death.attack.true_damage and its .player variant must be '......%1$s?'", 8);
            }
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    /**
     * Detects a death message that is still displayed as its raw translation key.
     *
     * <p>This happens when the lang entries are missing: the message then renders literally as
     * {@code death.attack.<msgId>}.
     *
     * <p>The test is simple: if the resolved string still starts with {@code death.attack.},
     * it was never translated. A translated message is natural language and cannot start with
     * that prefix.
     */
    static void checkRawDeathKey(ServerPlayer player, DamageSource source)
    {
        try
        {
            String localized = source.getLocalizedDeathMessage(player).getString();
            String tracker = player.getCombatTracker().getDeathMessage().getString();

            if (isRawDeathKey(localized) || isRawDeathKey(tracker))
                ProbeLog.logStack("DEATH-KEY",
                        "★ death message rendered as a raw key! localized='" + localized + "' tracker='" + tracker
                                + "' msgId=" + ProbeLog.safe(source::getMsgId)
                                + " => add `death.attack." + ProbeLog.safe(source::getMsgId)
                                + "` **and** the `.player` variant to lang (MC picks one depending on whether there is a killer)", 8);
        }
        catch (Throwable ignored)
        {
            // The probe must never affect gameplay.
        }
    }

    private static boolean isRawDeathKey(String message)
    {
        return message != null && message.startsWith("death.attack.");
    }

    static String describe(DamageSource source)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("msgId=").append(ProbeLog.safe(source::getMsgId));
        sb.append(" src=").append(name(source.getEntity()));
        sb.append(" direct=").append(name(source.getDirectEntity()));
        return sb.toString();
    }

    private static String name(Entity entity)
    {
        if (entity == null)
            return "null";

        return ProbeLog.safe(() -> entity.getType() + "#" + entity.getId());
    }

    /** Also reports whether sanitypd is loaded, so an empty log is not mistaken for a broken hook. */
    private static boolean hasSanityMod()
    {
        try
        {
            return Class.forName("piloser.sanitypd.SanityMod") != null;
        }
        catch (Throwable t)
        {
            return false;
        }
    }
}
