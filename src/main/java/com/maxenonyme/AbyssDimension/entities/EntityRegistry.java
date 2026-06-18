package com.maxenonyme.AbyssDimension.entities;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CreateAbyss.MOD_ID);

    public static final Supplier<EntityType<MagmaticSnailEntity>> MAGMATIC_SNAIL = ENTITY_TYPES.register("magmatic_snail",
            () -> EntityType.Builder.of(MagmaticSnailEntity::new, MobCategory.WATER_CREATURE)
                    .sized(1.8F, 2.0F)
                    .clientTrackingRange(8)
                    .build("magmatic_snail"));

    public static final Supplier<Item> MAGMATIC_SNAIL_SPAWN_EGG = CreateAbyss.ITEMS.register("magmatic_snail_spawn_egg",
            () -> new DeferredSpawnEggItem(MAGMATIC_SNAIL, 0x8B4513, 0xFF4500, new Item.Properties()));

    public static final Supplier<EntityType<IsopodEntity>> ISOPOD = ENTITY_TYPES.register("isopod",
            () -> EntityType.Builder.of(IsopodEntity::new, MobCategory.WATER_CREATURE)
                    .sized(0.9F, 0.5F)
                    .clientTrackingRange(8)
                    .build("isopod"));

    public static final Supplier<Item> ISOPOD_SPAWN_EGG = CreateAbyss.ITEMS.register("isopod_spawn_egg",
            () -> new DeferredSpawnEggItem(ISOPOD, 0x6B5B4F, 0x3A2E25, new Item.Properties()));

    public static final Supplier<EntityType<AmphistiumEntity>> AMPHISTIUM = ENTITY_TYPES.register("amphistium",
            () -> EntityType.Builder.of(AmphistiumEntity::new, MobCategory.WATER_AMBIENT)
                    .sized(0.6F, 0.4F)
                    .clientTrackingRange(4)
                    .build("amphistium"));

    public static final Supplier<Item> AMPHISTIUM_SPAWN_EGG = CreateAbyss.ITEMS.register("amphistium_spawn_egg",
            () -> new DeferredSpawnEggItem(AMPHISTIUM, 0x1A253C, 0x00D9C0, new Item.Properties()));

    public static final Supplier<EntityType<CookiecutterSharkEntity>> COOKIECUTTER_SHARK = ENTITY_TYPES.register("cookiecutter_shark",
            () -> EntityType.Builder.of(CookiecutterSharkEntity::new, MobCategory.WATER_CREATURE)
                    .sized(0.8F, 0.5F)
                    .clientTrackingRange(8)
                    .build("cookiecutter_shark"));

    public static final Supplier<Item> COOKIECUTTER_SHARK_SPAWN_EGG = CreateAbyss.ITEMS.register("cookiecutter_shark_spawn_egg",
            () -> new DeferredSpawnEggItem(COOKIECUTTER_SHARK, 0x12283A, 0x1E3B26, new Item.Properties()));

    public static void init(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(EntityRegistry::registerAttributes);
        modEventBus.addListener(EntityRegistry::registerSpawnPlacements);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AMPHISTIUM.get(), AmphistiumEntity.createAttributes().build());
        event.put(COOKIECUTTER_SHARK.get(), CookiecutterSharkEntity.createAttributes().build());
        event.put(MAGMATIC_SNAIL.get(), MagmaticSnailEntity.createAttributes().build());
        event.put(ISOPOD.get(), IsopodEntity.createAttributes().build());
    }

    public static void registerSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        event.register(
                AMPHISTIUM.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                AmphistiumEntity::checkSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.OR
        );
        event.register(
                COOKIECUTTER_SHARK.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                CookiecutterSharkEntity::checkSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.OR
        );
        event.register(
                MAGMATIC_SNAIL.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                MagmaticSnailEntity::checkSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.OR
        );
        event.register(
                ISOPOD.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.IN_WATER,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                IsopodEntity::checkSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.OR
        );
    }
}
