package piloser.sanitypd.entity.goal;

import piloser.sanitypd.SanityProcessor;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class TargetInsanePlayerGoal extends TargetGoal
{
    private final float m_sanityThreshold;
    /** Exit threshold used for hysteresis; a value below 0 disables it, matching the previous behaviour. */
    private final float m_exitThreshold;
    private boolean m_alertSameType;
    @Nullable
    private Class<?>[] m_toIgnoreAlert;
    private Player m_insanePlayer;

    public TargetInsanePlayerGoal(Mob pMob, boolean pMustSee, float sanityThreshold)
    {
        this(pMob, pMustSee, sanityThreshold, -1f);
    }

    /**
     * Constructor with hysteresis: {@code sanityThreshold} to acquire a target and {@code exitThreshold}
     * to drop it.
     *
     * <p>With a single threshold the mob re-acquires and loses its target every tick near the boundary,
     * flickering between charging and wandering. The wider exit threshold prevents that. Note the
     * filter here picks the <b>lowest</b> sanity above the threshold, so the exit threshold must be a
     * larger value.
     *
     * <p>{@code exitThreshold < 0} disables hysteresis, leaving behaviour identical to before.
     */
    public TargetInsanePlayerGoal(Mob pMob, boolean pMustSee, float sanityThreshold, float exitThreshold)
    {
        super(pMob, pMustSee);
        setFlags(EnumSet.of(Goal.Flag.TARGET));
        m_sanityThreshold = sanityThreshold;
        m_exitThreshold = exitThreshold;
    }

    public TargetInsanePlayerGoal(Mob pMob, boolean pMustSee)
    {
        this(pMob, pMustSee, -1f);
    }

    @Override
    public boolean canUse()
    {
        Player candidate = getMostInsanePlayer();

        // A player carrying sanitypd:inner_immunity must never be selected by an inner mob.
        // InnerEntity#tick also clears an already acquired immune target, covering other paths that
        // assign targets.
        if (candidate != null && candidate.hasEffect(piloser.sanitypd.effect.EffectRegistry.INNER_IMMUNITY.get()))
            candidate = null;

        // Hysteresis: while a target is still valid, keep chasing it using the wider exit threshold so
        // that sanity jitter around the threshold does not drop the target repeatedly. The target must
        // still satisfy exitThreshold.
        if (candidate == null && m_exitThreshold >= 0f && m_insanePlayer != null)
        {
            Player relaxed = getMostInsanePlayer(m_exitThreshold);
            if (relaxed != null
                    && !relaxed.hasEffect(piloser.sanitypd.effect.EffectRegistry.INNER_IMMUNITY.get()))
                candidate = relaxed;
        }

        return (m_insanePlayer = candidate) != null;
    }

    @Override
    public void start()
    {
        Player target = m_insanePlayer;
        if (target != null)
        {
            mob.setTarget(target);
            targetMob = mob.getTarget();
            if (m_alertSameType)
            {
                alertOthers();
            }
        }

        super.start();
    }

    public TargetInsanePlayerGoal setAlertOthers(Class<?>... pReinforcementTypes)
    {
        m_alertSameType = true;
        m_toIgnoreAlert = pReinforcementTypes;
        return this;
    }

    private Player getMostInsanePlayer()
    {
        return m_sanityThreshold < 0f ? SanityProcessor.getMostInsanePlayer(mob.level()) : SanityProcessor.getMostInsanePlayer(mob.level(), m_sanityThreshold);
    }

    /** Returns the most insane qualifying player using the given threshold; used for the hysteresis exit check. */
    private Player getMostInsanePlayer(float threshold)
    {
        return SanityProcessor.getMostInsanePlayer(mob.level(), threshold);
    }

    protected void alertOthers()
    {
        double d0 = this.getFollowDistance();
        AABB aabb = AABB.unitCubeFromLowerCorner(mob.position()).inflate(d0, 10.0D, d0);
        List<? extends Mob> list = mob.level().getEntitiesOfClass(mob.getClass(), aabb, EntitySelector.NO_SPECTATORS);
        Iterator iterator = list.iterator();

        while(true)
        {
            Mob mob;
            while(true)
            {
                if (!iterator.hasNext())
                {
                    return;
                }

                mob = (Mob)iterator.next();
                if (this.mob != mob && mob.getTarget() == null && (!(this.mob instanceof TamableAnimal) || ((TamableAnimal)this.mob).getOwner() == ((TamableAnimal)mob).getOwner()) && !mob.isAlliedTo(this.mob.getTarget()))
                {
                    if (m_toIgnoreAlert == null)
                    {
                        break;
                    }

                    boolean flag = false;

                    for(Class<?> oclass : m_toIgnoreAlert)
                    {
                        if (mob.getClass() == oclass)
                        {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag)
                    {
                        break;
                    }
                }
            }

            this.alertOther(mob, mob.getTarget());
        }
    }

    protected void alertOther(Mob pMob, LivingEntity pTarget) {
        pMob.setTarget(pTarget);
    }
}