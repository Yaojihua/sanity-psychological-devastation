package piloser.sanitypd;

import piloser.sanitypd.capability.*;
import piloser.sanitypd.config.*;
import piloser.sanitypd.entity.InnerEntity;
import piloser.sanitypd.entity.InnerEntitySpawner;
import piloser.sanitypd.item.ItemRegistry;
import piloser.sanitypd.net.InnerEntityCapImplPacket;
import piloser.sanitypd.net.PacketHandler;
import piloser.sanitypd.net.SanityPacket;
import piloser.sanitypd.passive.*;
import piloser.sanitypd.util.MathHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class SanityProcessor
{
    // The garland durability timer is stored per player (see Sanity#getGarlandTimer).
    private static final RandomSource RAND = RandomSource.create();

    public static final int MAX_GARLAND_TIMER = 60;
    public static final float SANITY_TARGET_THRESHOLD = .87f; // compared against getMadness() (0..1 madness)
    /**
     * Recalculation interval in ticks for the expensive passive sources: 10 = once per 0.5s.
     * Smaller reacts faster but costs more CPU; these sources change slowly, so 10 is plenty.
     */
    public static final int PASSIVE_SCAN_INTERVAL = 10;
    public static final List<IPassiveSanitySource> PASSIVE_SANITY_SOURCES = Arrays.asList(
            new Passive(),
            new InWaterOrRain(),
            new Hungry(),
            new EnderManAnger(),
            new Pet(),
            new Monster(),
            new Darkness(),
            new Lightness(),
            new PassiveBlocks(),
            new PlayerCompany(),
            new Jukebox(),
            new BlockStuck(),
            new DirtPath()
    );

    private SanityProcessor() {}

    private static float calcPassive(ServerPlayer player, ISanity sanity)
    {
        ResourceLocation dim = player.level().dimension().location();
        float passive = 0;

        // Cheap sources (attribute / effect / position checks) are still evaluated every tick.
        for (IPassiveSanitySource pss : PASSIVE_SANITY_SOURCES)
        {
            if (pss.isExpensive())
                continue;

            float val = pss.get(player, sanity, dim);
            passive += val * getSanityMultiplier(player, val);
        }

        // Expensive sources (entity scans, line-of-sight rays, per-block volume scans) are throttled
        // and reuse a cache: the numbers are identical, just up to PASSIVE_SCAN_INTERVAL ticks stale.
        passive += calcExpensivePassive(player, sanity, dim);

        int garlandTimer = sanity instanceof Sanity s0 ? s0.getGarlandTimer() : 0;
        garlandTimer--;
        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.is(ItemRegistry.GARLAND.get()))
        {
            // TODO: unhardcode
            // Garland: grants 0.005 sanity points per tick (positive value = sanity gain).
            passive += .005 * ConfigProxy.getPosMul(dim);
            if (garlandTimer <= 0)
                headItem.hurtAndBreak(player.isInWaterOrRain() ? 2 : 1, player, ent -> {});
        }
        if (garlandTimer <= 0)
            garlandTimer = MAX_GARLAND_TIMER;
            if (sanity instanceof Sanity s1)
                s1.setGarlandTimer(garlandTimer);

        return passive;
    }

    /**
     * Sum of the expensive passive sources, with a throttling cache.
     *
     * <p>These sources read slowly-changing world state (nearby monsters or pets, a campfire
     * underfoot), so a cached value equals a fresh scan, only up to
     * {@link #PASSIVE_SCAN_INTERVAL} ticks stale.
     */
    private static float calcExpensivePassive(ServerPlayer player, ISanity sanity, ResourceLocation dim)
    {
        if (!(sanity instanceof Sanity s))
            return sumExpensivePassive(player, sanity, dim);

        if (s.getPassiveScanTtl() > 0)
        {
            s.setPassiveScanTtl(s.getPassiveScanTtl() - 1);
            return s.getPassiveScanCache();
        }

        float sum = sumExpensivePassive(player, sanity, dim);
        s.setPassiveScanCache(sum);
        s.setPassiveScanTtl(PASSIVE_SCAN_INTERVAL);
        return sum;
    }

    private static float sumExpensivePassive(ServerPlayer player, ISanity sanity, ResourceLocation dim)
    {
        float sum = 0;

        for (IPassiveSanitySource pss : PASSIVE_SANITY_SOURCES)
        {
            if (!pss.isExpensive())
                continue;

            float val = pss.get(player, sanity, dim);
            sum += val * getSanityMultiplier(player, val);
        }

        return sum;
    }

    private static void shareSanity(ServerPlayer player, Sanity cap)
    {
        if (cap.getDirty())
        {
            SanityPacket packet = new SanityPacket(cap);
            PacketHandler.CHANNEL_INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
			cap.setDirty(false);
        }
    }

    private static void handlePlayerAte(ServerPlayer player, ItemStack itemStack)
    {
        handleActiveSourceForPlayer(
                player,
                ActiveSanitySources.EATING,
                ConfigProxy::getEatingCooldown,
                dim -> itemStack.getFoodProperties(player).getNutrition() * ConfigProxy.getEating(dim));
    }

    public static float getGarlandMultiplier(ServerPlayer player)
    {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ItemRegistry.GARLAND.get()) ? .92f : 1.0f;
    }

    public static float getSanityMultiplier(ServerPlayer player, float value)
    {
        ResourceLocation dim = player.level().dimension().location();
        return value >= 0 ? ConfigProxy.getPosMul(dim) : ConfigProxy.getNegMul(dim) * getGarlandMultiplier(player);
    }

    public static void addSanity(@NotNull ISanity sanity, float value, @NotNull ServerPlayer player)
    {
        if (value == 0.0f)
            return;

        sanity.setSanity(sanity.getSanity() + value * getSanityMultiplier(player, value));
    }

    public static void tickPlayer(final ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dim = player.level().dimension().location();

            float passive = calcPassive(player, s);
            float snapshot = s.getSanity();
            // passive premultiplied so no need for SanityProcessor#addSanity
            s.setSanity(s.getSanity() + passive);
            if (s instanceof IPassiveSanity ps)
            {
                ps.setPassiveIncrease(snapshot != s.getSanity() ? passive : 0);
            }
            if (s instanceof IPersistentSanity ps)
            {
                int[] cds = ps.getActiveSourcesCooldowns();
                for (int i = 0; i < cds.length; ++i)
                    cds[i] = Mth.clamp(cds[i] - 1, 0, Integer.MAX_VALUE);

                Map<Integer, Integer> itemCds = ps.getItemCooldowns();
                for (Iterator<Map.Entry<Integer, Integer>> it = itemCds.entrySet().iterator(); it.hasNext();)
                {
                    Map.Entry<Integer, Integer> entry = it.next();
                    itemCds.put(entry.getKey(), itemCds.get(entry.getKey()) - 1);
                    if (itemCds.get(entry.getKey()) <= 0)
                        it.remove();
                }

                Map<Integer, Integer> brokenBlocksCds = ps.getBrokenBlocksCooldowns();
                for (Iterator<Map.Entry<Integer, Integer>> it = brokenBlocksCds.entrySet().iterator(); it.hasNext();)
                {
                    Map.Entry<Integer, Integer> entry = it.next();
                    brokenBlocksCds.put(entry.getKey(), brokenBlocksCds.get(entry.getKey()) - 1);
                    if (brokenBlocksCds.get(entry.getKey()) <= 0)
                        it.remove();
                }
            }
            if (s instanceof Sanity)
                shareSanity(player, (Sanity)s);
        });
        InnerEntitySpawner.trySpawnForPlayer(player);
    }

    public static void tickLevel(final ServerLevel level)
    {
        for (Entity ent : level.getEntities().getAll())
        {
            if (ent instanceof InnerEntity ie)
            {
                ie.getCapability(InnerEntityCapImplProvider.CAP).ifPresent(iec ->
                {
                    if (iec instanceof InnerEntityCapImpl ieci)
                    {
                        // Also sync when the target player changed but hasTarget() did not.
                        if (ieci.hasTarget() != (ie.getTarget() != null) || !Objects.equals(ieci.getPlayerTargetUUID(), ie.getTarget() instanceof ServerPlayer sp ? sp.getUUID() : null))
                        {
                            ieci.setHasTarget(ie.getTarget() != null);
                            ieci.setPlayerTargetUUID(ie.getTarget() instanceof ServerPlayer sp ? sp.getUUID() : null);
                        }

                        if (ieci.getDirty())
                        {
                            InnerEntityCapImplPacket packet = new InnerEntityCapImplPacket(ieci);
                            packet.m_id = ent.getId();
                            PacketHandler.CHANNEL_INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> ent), packet);
                            ieci.setDirty(false); // must clear dirty after sending, or the same packet is resent every tick
                        }
                    }
                });
            }
        }
    }

    public static List<Player> getInsanePlayersInArea(final Level levelIn, BlockPos center, int blockRadius)
    {
        if (levelIn == null || center == null)
            return null;
        List<Player> list = new ArrayList<Player>();
        for (Player player : levelIn.getEntitiesOfClass(
                Player.class,
                new AABB(center.offset(blockRadius, blockRadius, blockRadius), center.offset(-blockRadius, -blockRadius, -blockRadius))))
        {
            player.getCapability(SanityProvider.CAP).ifPresent(s ->
            {
                if (s.getMadness() >= SANITY_TARGET_THRESHOLD)
                    list.add(player);
            });
        }
        return list;
    }

    public static Player getMostInsanePlayer(final Level levelIn)
    {
        return getMostInsanePlayer(levelIn, SANITY_TARGET_THRESHOLD);
    }

    public static Player getMostInsanePlayer(final Level levelIn, float sanityThreshold)
    {
        if (levelIn == null)
            return null;

        Player toReturn = null;
        float lowestSanity = Float.MAX_VALUE;
        for (Player player : levelIn.players())
        {
            if (player.isCreative() || player.isSpectator())
                continue;
            ISanity s = player.getCapability(SanityProvider.CAP).orElse(null);
            if (s == null)
                continue;
            float sanity = s.getSanity();
            if (s.getMadness() >= sanityThreshold && sanity < lowestSanity)
            {
                lowestSanity = sanity;
                toReturn = player;
            }
        }
        return toReturn;
    }

    public static void handleActiveSourceForPlayer(
            ServerPlayer player,
            int id,
            Function<ResourceLocation, Integer> cdSupplier,
            Function<ResourceLocation, Float> sanitySupplier)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            int cd = cdSupplier.apply(dimLoc);

            if (s instanceof IPersistentSanity ps && cd > 0.0f)
            {
                int timePassed = cd - ps.getActiveSourcesCooldowns()[id];
                addSanity(s, sanitySupplier.apply(dimLoc) * MathHelper.clampNorm((float) timePassed / cd), player);
//                s.setSanity(s.getSanity() + sanitySupplier.apply(dimLoc) * MathHelper.clampNorm((float) timePassed / cd));
                ps.getActiveSourcesCooldowns()[id] = cd;
            }
            else
                addSanity(s, sanitySupplier.apply(dimLoc), player);
//                s.setSanity(s.getSanity() + sanitySupplier.apply(dimLoc));
        });
    }

    public static void handlePlayerSlept(ServerLevel level)
    {
        for (ServerPlayer player : level.players())
        {
            if (player.isCreative() || player.isSpectator())
                continue;

            SanityProcessor.handleActiveSourceForPlayer(player, ActiveSanitySources.SLEEPING, ConfigProxy::getSleepingCooldown, ConfigProxy::getSleeping);
        }
    }

    public static void handlePlayerHurt(ServerPlayer player, float amount)
    {
        if (player == null || player.isCreative() || player.isSpectator() || amount <= 0)
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, amount * ConfigProxy.getHurtRatio(dimLoc), player);
//            s.setSanity(s.getSanity() + amount * ConfigProxy.getHurtRatio(player.level.dimension().location()));
        });
    }

    public static void handlePlayerHurtAnimal(ServerPlayer player, Animal animal, float amount)
    {
        if (player == null || player.isCreative() || player.isSpectator() || amount <= 0)
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, amount * ConfigProxy.getAnimalHurtRatio(player.level().dimension().location()) * (animal.isBaby() ? 2.0f : 1.0f), player);
//            s.setSanity(s.getSanity() + amount * ConfigProxy.getAnimalHurtRatio(player.level.dimension().location()) * (animal.isBaby() ? 2.0f : 1.0f));
        });
    }

    public static void handlePlayerPetDeath(ServerPlayer player, TamableAnimal pet)
    {
        // The player passed by this event already IS the pet owner; an extra
        // pet.isOwnedBy(player) check would prevent this penalty from ever firing.
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, ConfigProxy.getPetDeath(player.level().dimension().location()), player);
//            s.setSanity(s.getSanity() + ConfigProxy.getPetDeath(player.level.dimension().location()));
        });
    }

    public static void handlePlayerEnderManAngered(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s instanceof IPersistentSanity ps && ps.getEnderManAngerTimer() <= 0)
            {
                ps.setEnderManAngerTimer(100);
            }
        });
    }

    public static void handlePlayerGotAdvancement(ServerPlayer player, Advancement adv)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        if (adv.getDisplay() == null || !adv.getDisplay().shouldAnnounceChat())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            ResourceLocation dimLoc = player.level().dimension().location();
            addSanity(s, ConfigProxy.getAdvancement(dimLoc), player);
//            s.setSanity(s.getSanity() + ConfigProxy.getAdvancement(player.level.dimension().location()));
        });
    }

    public static void handlePlayerBredAnimals(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.BREEDING_ANIMALS, ConfigProxy::getAnimalBreedingCooldown, ConfigProxy::getAnimalBreeding);
    }

    public static void handlePlayerTradedWithVillager(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.VILLAGER_TRADE, ConfigProxy::getVillagerTradeCooldown, ConfigProxy::getVillagerTrade);
    }

    public static void handlePlayerUsedShears(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.SHEARING, ConfigProxy::getShearingCooldown, ConfigProxy::getShearing);
    }

    public static void handlePlayerSpawnedChicken(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(player, ActiveSanitySources.SPAWNING_BABY_CHICKEN, ConfigProxy::getBabyChickenSpawningCooldown, ConfigProxy::getBabyChickenSpawning);
    }

    public static void handlePlayerUsedItem(ServerPlayer player, ItemStack itemStack)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s instanceof IPersistentSanity ps)
            {
                ResourceLocation dim = player.level().dimension().location();

                for (ConfigItem citem : ConfigProxy.getItems(dim))
                {
                    if (!itemStack.is(ForgeRegistries.ITEMS.getValue(citem.m_name)))
                        continue;

                    if (!ConfigProxy.getIdToItemCat(dim).containsKey(citem.m_cat))
                    {
                        SanityMod.LOGGER.warn("player " + player.getDisplayName().getString() + " used " + citem.m_name + " from category " + citem.m_cat + ", but no such category is present");
                        return;
                    }

                    ConfigItemCategory cat = ConfigProxy.getIdToItemCat(dim).get(citem.m_cat);
                    if (cat.m_cd <= 0)
                    {
                        addSanity(s, citem.m_sanity, player);
                        return;
                    }

                    Map<Integer, Integer> itemCds = ps.getItemCooldowns();

                    if (!itemCds.containsKey(citem.m_cat) || itemCds.get(citem.m_cat) <= 0)
                    {
                        addSanity(s, citem.m_sanity, player);
                    }
                    else
                    {
                        int timePassed = cat.m_cd - itemCds.get(citem.m_cat);
                        addSanity(s, citem.m_sanity * MathHelper.clampNorm((float)timePassed / cat.m_cd), player);
                    }

                    itemCds.put(citem.m_cat, cat.m_cd);

                    return;
                }
            }

            if (itemStack.isEdible())
                handlePlayerAte(player, itemStack);
        });
    }

    public static void handlePlayerFishedItem(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        handleActiveSourceForPlayer(
                player,
                ActiveSanitySources.FISHING,
                ConfigProxy::getFishingCooldown,
                ConfigProxy::getFishing
        );
    }

    public static void handlePlayerMinedBlock(ServerPlayer player, BlockPos blockPos, BlockState blockState, Block block, boolean correctTool)
    {
        if (player == null)
            return;

        ServerLevel level = player.serverLevel();
        LevelChunk levelChunk = level.getChunkAt(blockPos);

        if (player.isCreative() || player.isSpectator())
        {
            levelChunk.getCapability(SanityLevelChunkProvider.CAP).ifPresent(sl ->
            {
                sl.getArtificiallyPlacedBlocks().remove(blockPos);
                levelChunk.setUnsaved(true);
            });
            return;
        }

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            if (s instanceof IPersistentSanity ps)
            {
                ResourceLocation dim = level.dimension().location();

                for (ConfigBrokenBlock cbblock : ConfigProxy.getBrokenBlocks(dim))
                {
                    if (!(cbblock.m_isTag && blockState.getTags().anyMatch(tag -> tag.location().equals(cbblock.m_name)) ||
                            block.equals(ForgeRegistries.BLOCKS.getValue(cbblock.m_name))))
                    {
                        continue;
                    }

                    if (cbblock.m_toolRequired && !correctTool)
                        return;

                    if (!ConfigProxy.getIdToBrokenBlockCat(dim).containsKey(cbblock.m_cat))
                    {
                        SanityMod.LOGGER.warn("player " + player.getDisplayName().getString() + " mined " + cbblock.m_name + " from category " + cbblock.m_cat + ", but no such category is present");
                        return;
                    }

                    // skip if artifically placed and block needs to be naturally gend
                    if (cbblock.m_naturallyGend)
                    {
                        AtomicBoolean flag = new AtomicBoolean(false);
                        levelChunk.getCapability(SanityLevelChunkProvider.CAP).ifPresent(sl ->
                        {
                            if (sl.getArtificiallyPlacedBlocks().remove(blockPos))
                            {
                                flag.set(true);
                                levelChunk.setUnsaved(true);
                            }
                        });
                        if (flag.get())
                            return;
                    }

                    ConfigBrokenBlockCategory cat = ConfigProxy.getIdToBrokenBlockCat(dim).get(cbblock.m_cat);
                    if (cat.m_cd <= 0)
                    {
                        addSanity(s, cbblock.m_sanity, player);
                        return;
                    }

                    Map<Integer, Integer> brokenBlockCds = ps.getBrokenBlocksCooldowns();

                    if (!brokenBlockCds.containsKey(cbblock.m_cat) || brokenBlockCds.get(cbblock.m_cat) <= 0)
                    {
                        addSanity(s, cbblock.m_sanity, player);
                    }
                    else
                    {
                        int timePassed = cat.m_cd - brokenBlockCds.get(cbblock.m_cat);
                        addSanity(s, cbblock.m_sanity * MathHelper.clampNorm((float)timePassed / cat.m_cd), player);
                    }

                    brokenBlockCds.put(cbblock.m_cat, cat.m_cd);

                    return;
                }
            }
        });
    }

    public static void handlePlayerTrampledFarmland(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            addSanity(s, ConfigProxy.getFarmlandTrample(player.level().dimension().location()), player);
        });
    }

    public static void handlePlayerPottedFlower(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            handleActiveSourceForPlayer(
                    player,
                    ActiveSanitySources.POTTING_FLOWER,
                    ConfigProxy::getPottingFlowerCooldown,
                    ConfigProxy::getPottingFlower
            );
        });
    }

    public static void handlePlayerChangedDimensions(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            addSanity(s, ConfigProxy.getChangedDimension(player.level().dimension().location()), player);
        });
    }

    public static void handlePlayerStruckByLightning(ServerPlayer player)
    {
        if (player == null || player.isCreative() || player.isSpectator())
            return;

        player.getCapability(SanityProvider.CAP).ifPresent(s ->
        {
            addSanity(s, ConfigProxy.getStruckByLightning(player.level().dimension().location()), player);
        });
    }
}