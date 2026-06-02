package com.maxenonyme.AbyssDimension.entities;

import com.maxenonyme.createsubmarine.CreateSubmarine;
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
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CreateSubmarine.MOD_ID);

    public static final Supplier<EntityType<AmphistiumEntity>> AMPHISTIUM = ENTITY_TYPES.register("amphistium",
            () -> EntityType.Builder.of(AmphistiumEntity::new, MobCategory.WATER_CREATURE)
                    .sized(0.6F, 0.4F)
                    .clientTrackingRange(4)
                    .build("amphistium"));

    public static final Supplier<Item> AMPHISTIUM_SPAWN_EGG = CreateSubmarine.ITEMS.register("amphistium_spawn_egg",
            () -> new DeferredSpawnEggItem(AMPHISTIUM, 0x1A253C, 0x00D9C0, new Item.Properties()));

    public static void init(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(EntityRegistry::registerAttributes);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AMPHISTIUM.get(), AmphistiumEntity.createAttributes().build());
    }
}
