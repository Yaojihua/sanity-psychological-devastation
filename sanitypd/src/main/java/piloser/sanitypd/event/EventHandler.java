package piloser.sanitypd.event;

import piloser.sanitypd.SanityMod;
import piloser.sanitypd.SanityTags;
import piloser.sanitypd.combat.SanityCombat;
import piloser.sanitypd.enchantment.PsychicDeprivationEnchantment;
import net.minecraftforge.event.AnvilUpdateEvent;
import piloser.sanitypd.SanityProcessor;
import piloser.sanitypd.capability.*;
import piloser.sanitypd.client.SoundPlayback;
import piloser.sanitypd.command.SanityCommand;
import piloser.sanitypd.entity.InnerEntity;
import piloser.sanitypd.entity.InnerEntitySpawner;
import piloser.sanitypd.item.IShadowWeapon;
import piloser.sanitypd.item.ShadowCharge;
import piloser.sanitypd.item.ShadowRefinement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.server.ServerLifecycleHooks;

public class EventHandler
{
    @SubscribeEvent
    public void registerCaps(final RegisterCapabilitiesEvent event)
    {
        event.register(ISanity.class);
        event.register(IInnerEntityCap.class);
        event.register(ISanityLevelChunk.class);
    }

    @SubscribeEvent
    public void attachEntityCaps(final AttachCapabilitiesEvent<Entity> event)
    {
        // Every living entity now has sanity (inner entities excepted): players cap at 100,
        // other mobs cap at their max health
        if (event.getObject() instanceof LivingEntity living && !SanityTags.isInnerEntity(living))
            event.addCapability(SanityProvider.KEY, new SanityProvider(living));
        else if (event.getObject() instanceof InnerEntity)
            event.addCapability(InnerEntityCapImplProvider.KEY, new InnerEntityCapImplProvider());
    }

    @SubscribeEvent
    public void attachLevelCaps(final AttachCapabilitiesEvent<LevelChunk> event)
    {
        event.addCapability(SanityLevelChunkProvider.KEY, new SanityLevelChunkProvider());
    }

    @SubscribeEvent
    public void tickPlayer(final TickEvent.PlayerTickEvent event)
    {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer sp)
            SanityProcessor.tickPlayer(sp);
    }

    @SubscribeEvent
    public void tickLevel(final TickEvent.LevelTickEvent event)
    {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel sl)
            SanityProcessor.tickLevel(sl);
    }

    @SubscribeEvent
    public void onLivingDamage(final LivingDamageEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
            SanityProcessor.handlePlayerHurt(player, event.getAmount());
    }

    /**
     * Hitting a friendly animal drains sanity.
     *
     * <p><b>Why this is hooked to {@link LivingHurtEvent} and not {@code LivingDamageEvent}:</b>
     * {@code LivingDamageEvent} carries the final (post-mitigation) amount and is not fired at all
     * once the damage is zeroed:
     * <ol>
     *   <li>the mod's psychic damage listener runs in {@code LivingHurtEvent} and does
     *       {@code setAmount(0) + setCanceled(true)}, which <b>stops {@code LivingDamageEvent} from
     *       propagating</b>, so a punishment triggered there is silently swallowed when a shadow
     *       weapon hits an animal;</li>
     *   <li>once the damage is zeroed, the amount read there is <b>0</b>, so not even "the animal was
     *       hit" can be detected.</li>
     * </ol>
     * In {@code LivingHurtEvent} the original amount is still available and the event fires before
     * the psychic damage handling, so "an animal was hit" is always observable.
     *
     * <p>Only "a player is the attacker and the target is not inside a damage-immunity window" is
     * checked; environmental damage (fall, fire) has no attacker and is ignored.
     */
    @SubscribeEvent
    public void onLivingHurtAnimal(final LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Animal animal))
            return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player))
            return;
        // The mod's own damage types (psychic / true / mania) do not count the penalty again:
        // a shadow weapon's psychic damage is an add-on to that hit, and the outer physical
        // hit has already been counted.
        if (SanityCombat.isSanityDamage(event.getSource()))
            return;
        SanityProcessor.handlePlayerHurtAnimal(player, animal, event.getAmount());
    }

    @SubscribeEvent
    public void onLivingDeath(final LivingDeathEvent event)
    {
        if (event.getEntity() instanceof TamableAnimal ta && ta.getOwnerUUID() != null)
            SanityProcessor.handlePlayerPetDeath(ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ta.getOwnerUUID()), ta);
    }

    @SubscribeEvent
    public void onPlayerGotAdvancement(final AdvancementEvent.AdvancementEarnEvent event)
    {
        SanityProcessor.handlePlayerGotAdvancement((ServerPlayer)event.getEntity(), event.getAdvancement());
    }

    @SubscribeEvent
    public void onPlayerBredAnimals(final BabyEntitySpawnEvent event)
    {
        if (event.getCausedByPlayer() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerBredAnimals(sp);
    }

    @SubscribeEvent
    public void onSleepFinished(final SleepFinishedTimeEvent event)
    {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel sl)
            SanityProcessor.handlePlayerSlept(sl);
    }

    @SubscribeEvent
    public void onTradeWithVillager(final TradeWithVillagerEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerTradedWithVillager(sp);
    }

    @SubscribeEvent
    public void onPlayerUsedItem(final LivingEntityUseItemEvent.Finish event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerUsedItem(sp, event.getItem());
    }

    @SubscribeEvent
    public void onItemFished(final ItemFishedEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerFishedItem(sp);
    }

    @SubscribeEvent
    public void onFarmlandTrample(final BlockEvent.FarmlandTrampleEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerTrampledFarmland(sp);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerChangedDimensions(sp);
    }

    @SubscribeEvent
    public void onPlayerStruckByLightning(final EntityStruckByLightningEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            SanityProcessor.handlePlayerStruckByLightning(sp);
    }

    // Clears the respawn timer when a player logs out, so the static map cannot grow without bound
    @SubscribeEvent
    public void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer sp)
            InnerEntitySpawner.PLAYER_TO_SPAWN_TIMEOUT.remove(sp.getUUID());
    }

    // ---- psychic / true damage / negative-effect combat rules (server side) ----
    @SubscribeEvent
    public void onLivingHurtSanity(final LivingHurtEvent event)
    {
        SanityCombat.onLivingHurt(event);
    }

    @SubscribeEvent
    public void onLivingDamageSanity(final LivingDamageEvent event)
    {
        SanityCombat.onLivingDamage(event);
    }

    @SubscribeEvent
    public void onLivingTickSanity(final LivingEvent.LivingTickEvent event)
    {
        SanityCombat.maintainStates(event.getEntity());
    }

    /**
     * Anvil operation window: only inside this window is the level cap of "psychic deprivation"
     * lifted to 255, so two level-4 books can be merged further up (the enchanting table, trading
     * and loot still cap at level 4).
     *
     * <p>Refinement is <b>not</b> handled here: cancelling {@code AnvilUpdateEvent} does <b>not</b>
     * write the result into the anvil (see the class comment of {@code MixinAnvilMenu}). Refinement
     * is taken over by {@code MixinAnvilMenu} at the start of {@code createResult()}; this method
     * only keeps the mending interception.
     */
    @SubscribeEvent
    public void onAnvilUpdate(final AnvilUpdateEvent event)
    {
        PsychicDeprivationEnchantment.openAnvilWindow();

        // Shadow weapons (sword/axe) cannot carry mending: the enchanting table path is blocked by
        // ShadowCharge#canApplyAtEnchantingTable, this is the anvil path. AnvilUpdateEvent documents
        // that cancelling it empties the result and skips the vanilla logic, so cancelling really
        // takes effect here (it is not merely "won't overwrite").
        if (event.getLeft().getItem() instanceof IShadowWeapon && ShadowCharge.blocksAnvil(event.getRight()))
            event.setCanceled(true);
    }

    /**
     * Taking out a refined weapon does <b>not</b> damage the anvil.
     *
     * <p>Why this has to be done here: vanilla {@code AnvilMenu#onTake} uses the same
     * {@code cost.get()} for both the break roll and the experience deduction, so keeping the
     * experience cost requires cost >= 1, which means the break roll always happens. Forge exposes
     * this event so that only the break <i>chance</i> ({@code breakChance}) can be changed, so it is
     * set to 0: the experience is still deducted and the anvil stays intact.
     *
     * <p>The check is "right slot holds an inner clump and left slot holds a shadow weapon", the same
     * combination {@code MixinAnvilMenu} uses, so vanilla and other mods' anvil use is unaffected.
     */
    @SubscribeEvent
    public void onAnvilRepairRefined(final AnvilRepairEvent event)
    {
        if (event.getLeft().getItem() instanceof IShadowWeapon
                && event.getRight().is(piloser.sanitypd.item.ItemRegistry.INNER_CLUMP.get()))
            event.setBreakChance(0f);
    }

    /**
     * A shadow weapon interrupted by "an attack that has an attacker" while charging loses its charge.
     *
     * <p>Only damage with an attacker is checked (melee/projectile, i.e. the kinds that knock back);
     * environmental damage (fall, fire, drowning) does not interrupt.
     */
    @SubscribeEvent
    public void onChargingPlayerHurt(final LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        if (event.getSource().getEntity() == null)
            return;
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof IShadowWeapon)
            player.stopUsingItem();
    }

    /** The window only lasts until the end of this tick, so it never affects random rolls such as loot. */
    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
            PsychicDeprivationEnchantment.closeAnvilWindow();
    }

    @SubscribeEvent
    public void registerCommands(final RegisterCommandsEvent event)
    {
        SanityMod.LOGGER.info("Registering sanity command...");
        SanityCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onVanillaGameEvent(final VanillaGameEvent event)
    {
        if (event.getVanillaEvent() == GameEvent.BLOCK_PLACE)
        {
            Vec3 pos = event.getEventPosition();
            BlockPos bPos = BlockPos.containing(pos.x, pos.y, pos.z);
            event.getLevel().getChunkAt(bPos).getCapability(SanityLevelChunkProvider.CAP).ifPresent(slc ->
            {
                slc.getArtificiallyPlacedBlocks().add(bPos);
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void tickLocalPlayer(final TickEvent.PlayerTickEvent event)
    {
        if (event.side == LogicalSide.CLIENT && event.phase == TickEvent.Phase.END && event.player instanceof LocalPlayer)
        {
            SoundPlayback.playSounds((LocalPlayer)event.player);
            SanityMod.getInstance().getGui().tick(Minecraft.getInstance().getPartialTick());
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void localLevelLoad(final LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ClientLevel)
            SoundPlayback.onClientLevelLoad((ClientLevel) event.getLevel());
    }
}