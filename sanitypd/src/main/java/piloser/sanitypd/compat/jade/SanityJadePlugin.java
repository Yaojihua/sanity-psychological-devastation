package piloser.sanitypd.compat.jade;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.SanityTags;
import piloser.sanitypd.capability.SanityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;

import java.util.Locale;

/**
 * Jade integration: shows a mini brain icon plus current/max sanity below the health bar.
 *
 * <p>Implemented through Jade's server data provider API, so only the entity under the crosshair is
 * queried and sanity is never broadcast for every mob.
 *
 * <p>The icon is an 11x11 pixel reduction of the 32x32 brain from the original Sanity mod, sharing
 * its palette (main #D5AE9E / outline #5B2728 / sulci #774349 / highlight #E8CEC6). Icon and numbers
 * are drawn as a single {@link IElement}, so they always land on one row regardless of how Jade
 * wraps multiple elements.
 *
 * <p>Jade is a soft dependency: this class is only loaded by Jade's annotation scanner when Jade is
 * installed.
 */
@WailaPlugin(SanityMod.MODID)
public class SanityJadePlugin implements IWailaPlugin
{
    /** Shared UID for the client component and the server data provider. */
    public static final ResourceLocation UID = new ResourceLocation(SanityMod.MODID, "sanity");

    /** NBT keys written by the server data provider. */
    public static final String NBT_SANITY = "sanitypd_sanity";
    public static final String NBT_MAX_SANITY = "sanitypd_max_sanity";

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerEntityDataProvider(SanityServerData.INSTANCE, LivingEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.registerEntityComponent(SanityEntityComponent.INSTANCE, LivingEntity.class);
    }
}

/** Server side: writes the targeted entity's sanity into Jade's synced data. */
enum SanityServerData implements IServerDataProvider<EntityAccessor>
{
    INSTANCE;

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor)
    {
        Entity entity = accessor.getEntity();
        if (!(entity instanceof LivingEntity living) || SanityTags.isInnerEntity(living))
            return;

        living.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            data.putFloat(SanityJadePlugin.NBT_SANITY, s.getSanity());
            data.putFloat(SanityJadePlugin.NBT_MAX_SANITY, s.getMaxSanity());
        });
    }

    @Override
    public ResourceLocation getUid()
    {
        return SanityJadePlugin.UID;
    }
}

/** Client side: adds the brain icon + value line under the health bar (vanilla health is -4501, so -4502 here). */
enum SanityEntityComponent implements IEntityComponentProvider
{
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config)
    {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(SanityJadePlugin.NBT_SANITY))
            return;

        float sanity = data.getFloat(SanityJadePlugin.NBT_SANITY);
        float max = data.getFloat(SanityJadePlugin.NBT_MAX_SANITY);
        // No "Sanity" label: just the mini brain icon and the numbers.
        tooltip.add(new BrainLine(format(sanity) + "/" + format(max)));
    }

    @Override
    public ResourceLocation getUid()
    {
        return SanityJadePlugin.UID;
    }

    @Override
    public int getDefaultPriority()
    {
        return -4502;
    }

    /** Whole numbers print without decimals, otherwise one decimal place. */
    private static String format(float value)
    {
        return Math.abs(value - Math.round(value)) < 0.05f
                ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}

/**
 * Combines the mini brain icon and the current/max numbers into one Jade element.
 *
 * <p>The size is fixed to (icon width + gap + text width) x icon height, which makes Jade lay it out
 * on the same row as the health bar, left aligned with it.
 */
final class BrainLine implements IElement
{
    /** 11x11 mini brain, redrawn from the original Sanity mod's 32x32 brain in the same palette. */
    private static final ResourceLocation ICON =
            new ResourceLocation(SanityMod.MODID, "textures/gui/mini_brain.png");

    private static final int ICON_SIZE = 11;
    private static final int GAP = 3;
    private static final int TEXT_COLOR = 0xFFFFFF;

    private final String m_text;
    private final Vec2 m_size;

    BrainLine(String text)
    {
        m_text = text;
        int textWidth;
        try
        {
            textWidth = Minecraft.getInstance().font.width(text);
        }
        catch (Throwable ignored)
        {
            textWidth = text.length() * 6;   // Fallback; not expected to happen
        }
        m_size = new Vec2(ICON_SIZE + GAP + textWidth, ICON_SIZE);
    }

    @Override
    public void render(GuiGraphics gui, float x, float y, float width, float height)
    {
        gui.blit(ICON, (int) x, (int) y + 1, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        gui.drawString(Minecraft.getInstance().font, m_text,
                (int) (x + ICON_SIZE + GAP), (int) y + 3, TEXT_COLOR);
    }

    // ------------------------------------------------------------------ remaining IElement members

    @Override
    public IElement size(Vec2 size)
    {
        return this;    // size is content driven, no external override
    }

    @Override
    public Vec2 getSize()
    {
        return m_size;
    }

    @Override
    public Vec2 getCachedSize()
    {
        return m_size;
    }

    @Override
    public IElement align(IElement.Align align)
    {
        return this;
    }

    @Override
    public IElement.Align getAlignment()
    {
        return IElement.Align.LEFT;
    }

    @Override
    public IElement translate(Vec2 translation)
    {
        return this;
    }

    @Override
    public Vec2 getTranslation()
    {
        return Vec2.ZERO;
    }

    @Override
    public IElement tag(ResourceLocation tag)
    {
        return this;
    }

    @Override
    public ResourceLocation getTag()
    {
        return null;
    }

    /** Plain text used when copying to the clipboard or narrating. */
    @Override
    public String getCachedMessage()
    {
        return m_text;
    }

    @Override
    public IElement clearCachedMessage()
    {
        return this;
    }

    @Override
    public IElement message(String message)
    {
        return this;
    }
}
