package piloser.sanitypd.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.config.ConfigProxy;

/**
 * Blood pools on the ground: the client-side decal layer behind a self-injury act.
 *
 * <h2>What it is and what it is not</h2>
 * The design requires a decal rather than world content, so nothing here touches a chunk, a block, a block
 * entity or a save file: a pool is a record in memory (position, facing, rotation, variant, age, packed light)
 * that is drawn as one textured quad lying on a surface. It is therefore per-client by construction - two
 * players in one world each see their own - and it disappears when the world is left. Making pools permanent
 * and identical for everyone would mean a real block with a blockstate, a model, a texture set, loot and an
 * audit entry, which is a different feature.
 *
 * <h2>The rules, and where each one lives</h2>
 * <ul>
 *   <li><b>Only on a block, never in a fluid and never in the air.</b> {@link #findSurface} walks down from
 *       the player's feet and accepts only a block whose collision shape has a top surface. Water and lava have
 *       no collision shape, so "standing in a fluid" and "standing on a fluid" both fail the same test, and a
 *       player who is falling, flying or standing over a hole simply gets no pool - the alternative was blood
 * hanging in mid-air, which is the defect the name came from. The volume the pool would occupy is checked
 *       again, so a pool cannot be placed inside the water of a shallow puddle either.</li>
 *   <li><b>One pool per second for five seconds after the act</b> - the schedule lives in
 *       {@code SelfInjuryPresentation}, on the same unconditional timeline as the rest of the act, so no
 *       branch being skipped can freeze it.</li>
 *   <li><b>Thirty seconds, then five seconds of fading out</b> - {@link #LIFE_TICKS} and {@link #FADE_TICKS},
 *       applied as a multiplier on the quad's alpha.</li>
 *   <li><b>Small enough to be believable</b> - {@link #SIZE} is the quad's width in blocks, about a fifth of
 *       a block, and the three textures are drawn to match.</li>
 * </ul>
 *
 * <h2>Rendering notes</h2>
 * Drawn in {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} so a pool sits on top of the block it
 * lies on: the quad is lifted {@link #LIFT} above the surface (a polygon offset would also work; a lift is one
 * number and cannot interact with another pass's state), and its light is the packed light of the block the
 * pool lies on, read once when the pool is created - a pool that ignored light would glow in the dark. The
 * vertices go through their own immediate buffer source, so this pass cannot disturb whatever the level
 * renderer had queued.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SanityMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodDecals
{
    /** How long a pool lives, in ticks (30 s). */
    public static final int LIFE_TICKS = 600;

    /** How long it takes to fade away at the end of that life, in ticks (5 s). */
    public static final int FADE_TICKS = 100;

    /** Width of one pool in blocks. Deliberately small: a person does not bleed a puddle. */
    public static final float SIZE = 0.34f;

    /** Upper bound on live pools; the oldest is dropped first, so a long session cannot grow without end. */
    public static final int MAX_DECALS = 128;

    /** How far from the player's own position a pool may be placed, in blocks. */
    public static final float SPAWN_SPREAD = 0.30f;

    /** How far below the player's feet a surface may be and still count as "standing on it". */
    private static final double SURFACE_SEARCH = 1.5d;

    /** Lift above the surface, in blocks: one thousandth is invisible and is enough to win against the block. */
    private static final float LIFT = 0.002f;

 /** The three variants drawn and approved for this feature; picked at random, with a random quarter turn. */
    private static final ResourceLocation[] VARIANTS =
    {
        texture("blood_stain_1"),
        texture("blood_stain_2"),
        texture("blood_stain_3"),
    };

    private static final Random RAND = new Random();

    /** One pool: where it lies, how it is turned, which variant it is, how old it is and its light. */
    private static final class Splat
    {
        final ResourceLocation texture;
        final double x;
        final double y;
        final double z;
        final float rotation;
        final int light;
        final String dimension;
        float age;

        Splat(ResourceLocation texture, double x, double y, double z, float rotation, int light, String dimension)
        {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
            this.light = light;
            this.dimension = dimension;
        }
    }

    private static final Deque<Splat> DECALS = new ArrayDeque<>();

    // --- counters, for the diagnostic probe: "nothing was drawn" and "nothing ran" must stay distinguishable
    private static int s_spawned;
    private static int s_rejected;
    private static int s_drawnFrames;
    private static double s_lastSpawnY = Double.NaN;

    private BloodDecals() {}

    private static ResourceLocation texture(String name)
    {
        return new ResourceLocation(SanityMod.MODID, "textures/decal/" + name + ".png");
    }

    // ------------------------------------------------------------------ spawning

    /**
     * Places one pool near the local player, if there is a block surface to put it on.
     *
     * @return whether a pool was placed; a rejection is counted, not logged, because a player in the air or in
     *         water is a normal state and not a fault
     */
    public static boolean spawnNearPlayer()
    {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc == null ? null : mc.player;
        Level level = mc == null ? null : mc.level;

        if (player == null || level == null)
        {
            s_rejected++;
            return false;
        }

 // The rule, first half: a pool needs a block under it. Standing in a fluid fails here as well,
        // because a fluid has no collision shape to stand on.
        Surface own = findSurface(level, player.position());

        if (own == null)
        {
            s_rejected++;
            return false;
        }

        // A small offset so five pools from one act do not land on one another. The offset point has to find the
        // same surface height, otherwise a pool would hang over a step or a hole; falling back to the player's
        // own spot keeps the pool where it is known to be valid.
        double x = own.x;
        double z = own.z;

        if (SPAWN_SPREAD > 0f)
        {
            double ox = own.x + (RAND.nextDouble() * 2d - 1d) * SPAWN_SPREAD;
            double oz = own.z + (RAND.nextDouble() * 2d - 1d) * SPAWN_SPREAD;
            Surface offset = findSurface(level, new Vec3(ox, player.getY(), oz));

            if (offset != null && Math.abs(offset.y - own.y) < 0.06d)
            {
                x = offset.x;
                z = offset.z;
            }
        }

        add(level, x, own.y, z);
        s_lastSpawnY = own.y;
        return true;
    }

    /** Adds one pool at an already validated position. */
    private static void add(Level level, double x, double y, double z)
    {
        BlockPos above = BlockPos.containing(x, y + 0.01d, z);
        int light = LevelRenderer.getLightColor(level, above);
        String dimension = level.dimension().location().toString();

        DECALS.addLast(new Splat(VARIANTS[RAND.nextInt(VARIANTS.length)], x, y + LIFT, z,
                RAND.nextInt(4) * 90f, light, dimension));
        s_spawned++;

        while (DECALS.size() > MAX_DECALS)
            DECALS.removeFirst();
    }

    /**
     * The surface a pool may be placed on at the given position, or {@code null} when there is none.
     *
     * <p>Walks down at most {@link #SURFACE_SEARCH} blocks. A fluid anywhere in that column ends the search:
 * the design requires pools to be ineffective in fluids, and a pool inside a puddle would look like a
     * decal, not like blood in water. An unloaded chunk ends it too - a pool placed there would be drawn over
     * whatever the client has not loaded yet.
     */
    private static Surface findSurface(Level level, Vec3 from)
    {
        int startY = Mth.floor(from.y - 0.05d);
        int lowestY = Mth.floor(from.y - SURFACE_SEARCH);

        for (int y = startY; y >= lowestY; y--)
        {
            BlockPos pos = BlockPos.containing(from.x, y, from.z);

            if (!level.isLoaded(pos))
                return null;

            BlockState state = level.getBlockState(pos);

            if (!state.getFluidState().isEmpty())
                return null;

            VoxelShape shape = state.getCollisionShape(level, pos);

            if (shape.isEmpty())
                continue;

            double top = shape.max(Direction.Axis.Y);

            if (top <= 0.05d)
                continue;

            double surfaceY = pos.getY() + top;

            if (surfaceY > from.y + 0.05d)
                continue;

            // The volume the pool will occupy must be free of fluid as well (a shallow puddle over a solid
            // bottom is exactly the case that would otherwise slip through).
            BlockPos spawnPos = BlockPos.containing(from.x, surfaceY + 0.01d, from.z);

            if (!level.isLoaded(spawnPos) || !level.getFluidState(spawnPos).isEmpty())
                return null;

            return new Surface(from.x, surfaceY, from.z);
        }

        return null;
    }

    /** A validated placement: the point it was asked for, plus the surface height found under it. */
    private record Surface(double x, double y, double z) {}

    // ------------------------------------------------------------------ lifetime

    /**
     * Ages every pool and drops the finished ones.
     *
     * <p>Called from the same unconditional client tick as the rest of the act's presentation, for the same
     * reason: a counter that is only advanced inside a draw branch freezes whenever that branch is skipped, and
     * a frozen pool would sit on the ground forever.
     */
    public static void tick(float dt)
    {
        if (DECALS.isEmpty())
            return;

        DECALS.removeIf(splat ->
        {
            splat.age += dt;
            return splat.age >= LIFE_TICKS;
        });
    }

    /** Drops every pool; called when the client leaves a world, so nothing leaks into the next one. */
    public static void clear()
    {
        DECALS.clear();
    }

    /** Full opacity for most of the life, then a linear fade over the last {@link #FADE_TICKS}. */
    private static float alphaOf(Splat splat)
    {
        float left = LIFE_TICKS - splat.age;

        return left >= FADE_TICKS ? 1f : Mth.clamp(left / (float) FADE_TICKS, 0f, 1f);
    }

    // ------------------------------------------------------------------ rendering

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || DECALS.isEmpty())
            return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null)
            return;

        // The blood layer follows the same per-dimension switch as the rest of the mod's blood visuals.
        if (!ConfigProxy.getRenderBtOverlay(mc.level.dimension().location()))
            return;

        String dimension = mc.level.dimension().location().toString();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        int drawn = 0;

        for (Splat splat : DECALS)
        {
            if (!splat.dimension.equals(dimension))
                continue;

            float alpha = alphaOf(splat);

            if (alpha <= 0.01f)
                continue;

            double dx = splat.x - camera.x;
            double dy = splat.y - camera.y;
            double dz = splat.z - camera.z;

            if (dx * dx + dz * dz > 64d * 64d)
                continue;

            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(splat.texture));
            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            poseStack.mulPose(Axis.YP.rotationDegrees(splat.rotation));

            Matrix4f matrix = poseStack.last().pose();
            float half = SIZE / 2f;

            quad(consumer, matrix, half, alpha, splat.light);
            poseStack.popPose();
            drawn++;
        }

        buffers.endBatch();

        if (drawn > 0)
            s_drawnFrames++;
    }

    /** One flat quad on the XZ plane, wound so it faces up. */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, float half, float alpha, int light)
    {
        // uv2 carries the two lightmap components separately (block, sky), not the packed value.
        int lightU = light & 0xFFFF;
        int lightV = (light >> 16) & 0xFFFF;
        consumer.vertex(matrix, -half, 0f, -half).color(1f, 1f, 1f, alpha).uv(0f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lightU, lightV).normal(0f, 1f, 0f).endVertex();
        consumer.vertex(matrix, -half, 0f, half).color(1f, 1f, 1f, alpha).uv(0f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lightU, lightV).normal(0f, 1f, 0f).endVertex();
        consumer.vertex(matrix, half, 0f, half).color(1f, 1f, 1f, alpha).uv(1f, 1f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lightU, lightV).normal(0f, 1f, 0f).endVertex();
        consumer.vertex(matrix, half, 0f, -half).color(1f, 1f, 1f, alpha).uv(1f, 0f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lightU, lightV).normal(0f, 1f, 0f).endVertex();
    }

    // ------------------------------------------------------------------ diagnostics

    /** How many pools are alive right now. */
    public static int activeCount()
    {
        return DECALS.size();
    }

    /** How many pools have been placed since the client started. */
    public static int spawnedCount()
    {
        return s_spawned;
    }

    /** How many placements were refused because there was no block surface to put a pool on. */
    public static int rejectedCount()
    {
        return s_rejected;
    }

    /** How many frames drew at least one pool; the probe needs this to tell drawing from never drawing. */
    public static int drawnFrameCount()
    {
        return s_drawnFrames;
    }

    /** Height of the last accepted surface, or {@code NaN}; lets the probe judge "on a block, not in the air". */
    public static double lastSpawnY()
    {
        return s_lastSpawnY;
    }
}
