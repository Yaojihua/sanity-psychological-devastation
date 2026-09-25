package piloser.sanitypd.entity;

import piloser.sanitypd.SanityMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class EntityRegistry
{
    public static final DeferredRegister<EntityType<?>> DEFERRED_REGISTER = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SanityMod.MODID);

    public static final RegistryObject<EntityType<RottingStalker>> ROTTING_STALKER
            = DEFERRED_REGISTER.register("rotting_stalker",
            () -> EntityType.Builder.of(RottingStalker::new, MobCategory.MONSTER).sized(1f, 2.9f).fireImmune().build("rotting_stalker"));

    public static final RegistryObject<EntityType<SneakingTerror>> SNEAKING_TERROR
            = DEFERRED_REGISTER.register("sneaking_terror",
            () -> EntityType.Builder.of(SneakingTerror::new, MobCategory.MONSTER).sized(1.3f, 4f).fireImmune().build("sneaking_terror"));

    /**
     * Screaming crawler: modelled at the vanilla creeper proportions scaled by 3.69
     * (total height 48 MC units = 3.00 blocks).
     *
     * <p><b>The hitbox must match the model</b>: the model bounding box spans x -7.39..7.39
     * (width 14.77 pixels = <b>0.92 blocks</b>) and y 0..48 (height <b>3.00 blocks</b>).
     * A smaller hitbox makes the mob clip through blocks and also breaks pathfinding, attack hit
     * detection and spawn clearance checks.
     */
    public static final RegistryObject<EntityType<ScreamingCrawler>> SCREAMING_CRAWLER
            = DEFERRED_REGISTER.register("screaming_crawler",
            () -> EntityType.Builder.of(ScreamingCrawler::new, MobCategory.MONSTER).sized(0.92f, 3.0f).fireImmune().build("screaming_crawler"));

    public static final List<Supplier<EntityType<? extends InnerEntity>>> INNER_ENTITIES = Arrays.asList(
            ROTTING_STALKER::get,
            SNEAKING_TERROR::get,
            SCREAMING_CRAWLER::get);

    public static void register(IEventBus eventBus)
    {
        DEFERRED_REGISTER.register(eventBus);
    }
}