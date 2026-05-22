package com.maxenonyme.createsubmarine;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.maxenonyme.createsubmarine.submarine.block.*;
import com.maxenonyme.createsubmarine.submarine.block.entity.*;
import com.maxenonyme.createsubmarine.submarine.effect.SuffocationEffect;
import com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem;
import com.maxenonyme.createsubmarine.submarine.system.*;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.neoforged.fml.config.ModConfig;

@Mod(CreateSubmarine.MOD_ID)
public class CreateSubmarine {
        public static final String MOD_ID = "create_submarine";
        public static final Logger LOGGER = LogUtils.getLogger();
        public static final boolean DISABLE_WATER_OCCLUSION = false;
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
                        .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
        public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT,
                        MOD_ID);
        public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister
                        .create(BuiltInRegistries.MENU, MOD_ID);
        public static final DeferredRegister<net.minecraft.world.effect.MobEffect> MOB_EFFECTS = DeferredRegister
                        .create(Registries.MOB_EFFECT, MOD_ID);
        public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS = DeferredRegister
                        .create(Registries.FLUID, MOD_ID);

        public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES, MOD_ID);

        public static final net.neoforged.neoforge.registries.DeferredHolder<FluidType, FluidType> OXYGEN_TYPE = FLUID_TYPES
                        .register("oxygen", () -> new FluidType(net.neoforged.neoforge.fluids.FluidType.Properties.create()
                                        .descriptionId("fluid.create_submarine.oxygen")
                                        .density(-1000)
                                        .viscosity(1000)) {
                                @Override
                                public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                                        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                                                @Override
                                                public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
                                                @Override
                                                public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
                                                @Override
                                                public int getTintColor() { return 0x88FFFFFF; }
                                        });
                                }
                        });

        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.material.Fluid, net.minecraft.world.level.material.FlowingFluid> OXYGEN = FLUIDS
                        .register("oxygen", () -> new net.neoforged.neoforge.fluids.BaseFlowingFluid.Source(makeOxygenProperties()));

        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.material.Fluid, net.minecraft.world.level.material.FlowingFluid> OXYGEN_FLOWING = FLUIDS
                        .register("oxygen_flowing", () -> new net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing(makeOxygenProperties()));

        private static net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties makeOxygenProperties() {
                return new net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties(
                        OXYGEN_TYPE, OXYGEN, OXYGEN_FLOWING);
        }
        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect> SUFFOCATION = MOB_EFFECTS
                        .register("suffocation",
                                        SuffocationEffect::new);
        public static final Supplier<Block> CREATIVE_OXYGENATOR = BLOCKS.register("creative_oxygenator",
                        () -> new HullControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
        public static final Supplier<Item> CREATIVE_OXYGENATOR_ITEM = ITEMS.register("creative_oxygenator",
                        () -> new com.maxenonyme.createsubmarine.submarine.block.CreativeOxygenatorItem(CREATIVE_OXYGENATOR.get(), new net.minecraft.world.item.Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
        public static final Supplier<BlockEntityType<HullControllerBlockEntity>> CREATIVE_OXYGENATOR_BE = BLOCK_ENTITIES
                        .register("creative_oxygenator",
                                        () -> BlockEntityType.Builder
                                                        .of(HullControllerBlockEntity::new, CREATIVE_OXYGENATOR.get())
                                                        .build(null));
        public static final Supplier<Block> BALLAST_TANK = BLOCKS.register("ballast_tank",
                        () -> new BallastTankBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
        public static final Supplier<Item> BALLAST_TANK_ITEM = ITEMS.register("ballast_tank",
                        () -> new com.maxenonyme.createsubmarine.submarine.block.BallastTankItem(BALLAST_TANK.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<BallastTankBlockEntity>> BALLAST_TANK_BE = BLOCK_ENTITIES.register(
                        "ballast_tank",
                        () -> BlockEntityType.Builder.of(BallastTankBlockEntity::new, BALLAST_TANK.get()).build(null));
        public static final Supplier<Block> BALLAST_VENT = BLOCKS.register("ballast_vent",
                        () -> new BallastVentBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> BALLAST_VENT_ITEM = ITEMS.register("ballast_vent",
                        () -> new net.minecraft.world.item.BlockItem(BALLAST_VENT.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<BallastVentBlockEntity>> BALLAST_VENT_BE = BLOCK_ENTITIES.register(
                        "ballast_vent",
                        () -> BlockEntityType.Builder.of(BallastVentBlockEntity::new, BALLAST_VENT.get()).build(null));
        public static final Supplier<Block> OXYGENE_DIFFUSER = BLOCKS.register("oxygene_diffuser",
                        () -> new OxygeneDiffuserBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion()));
        public static final Supplier<Item> OXYGENE_DIFFUSER_ITEM = ITEMS.register("oxygene_diffuser",
                        () -> new net.minecraft.world.item.BlockItem(OXYGENE_DIFFUSER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<OxygeneDiffuserBlockEntity>> OXYGENE_DIFFUSER_BE = BLOCK_ENTITIES
                        .register("oxygene_diffuser",
                                        () -> BlockEntityType.Builder
                                                        .of(OxygeneDiffuserBlockEntity::new, OXYGENE_DIFFUSER.get())
                                                        .build(null));
        public static final Supplier<SoundEvent> IMPLOSION_SOUND = SOUNDS.register("implosion",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "implosion")));
        public static final Supplier<Block> ELECTROLYZER = BLOCKS.register("electrolyzer",
                        () -> new ElectrolyzerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                                        .noOcclusion()
                                        .isViewBlocking((state, level, pos) -> false)
                                        .isSuffocating((state, level, pos) -> false)));
        public static final Supplier<Item> ELECTROLYZER_ITEM = ITEMS.register("electrolyzer",
                        () -> new net.minecraft.world.item.BlockItem(ELECTROLYZER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER_BE = BLOCK_ENTITIES.register(
                        "electrolyzer",
                        () -> BlockEntityType.Builder.of(ElectrolyzerBlockEntity::new, ELECTROLYZER.get()).build(null));
        public static final Supplier<Block> INDUSTRIAL_ALARM = BLOCKS.register("industrial_alarm",
                        () -> new com.maxenonyme.createsubmarine.submarine.block.IndustrialAlarmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> INDUSTRIAL_ALARM_ITEM = ITEMS.register("industrial_alarm",
                        () -> new net.minecraft.world.item.BlockItem(INDUSTRIAL_ALARM.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<com.maxenonyme.createsubmarine.submarine.block.entity.IndustrialAlarmBlockEntity>> INDUSTRIAL_ALARM_BE = BLOCK_ENTITIES.register(
                        "industrial_alarm",
                        () -> BlockEntityType.Builder.of(com.maxenonyme.createsubmarine.submarine.block.entity.IndustrialAlarmBlockEntity::new, INDUSTRIAL_ALARM.get()).build(null));
        public static final Supplier<Block> WATER_THRUSTER = BLOCKS.register("water_thruster",
                        () -> new WaterThrusterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> WATER_THRUSTER_ITEM = ITEMS.register("water_thruster",
                        () -> new net.minecraft.world.item.BlockItem(WATER_THRUSTER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<WaterThrusterBlockEntity>> WATER_THRUSTER_BE = BLOCK_ENTITIES.register(
                        "water_thruster",
                        () -> BlockEntityType.Builder.of(WaterThrusterBlockEntity::new, WATER_THRUSTER.get()).build(null));
        public static final Supplier<net.minecraft.world.inventory.MenuType<com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerMenu>> ELECTROLYZER_MENU = MENUS
                        .register("electrolyzer",
                                        () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerMenu::new));
        public static final Supplier<Block> GLASS_PRESSURIZER = BLOCKS.register("glass_pressurizer",
                        () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                                        .noOcclusion()
                                        .isViewBlocking((state, level, pos) -> false)
                                        .isSuffocating((state, level, pos) -> false)));
        public static final Supplier<Item> GLASS_PRESSURIZER_ITEM = ITEMS.register("glass_pressurizer",
                        () -> new net.minecraft.world.item.BlockItem(GLASS_PRESSURIZER.get(), new Item.Properties()));

        public static final Supplier<Block> FLOATER = BLOCKS.register("floater",
                        () -> new FloaterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> FLOATER_ITEM = ITEMS.register("floater",
                        () -> new net.minecraft.world.item.BlockItem(FLOATER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<FloaterBlockEntity>> FLOATER_BE = BLOCK_ENTITIES.register(
                        "floater",
                        () -> BlockEntityType.Builder.of(FloaterBlockEntity::new, FLOATER.get()).build(null));

        public CreateSubmarine(IEventBus modEventBus, ModContainer modContainer) {
                net.minecraft.core.Registry.register(
                        dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY,
                        StressForceFeedSystem.STRESS_ID,
                        new dev.ryanhcode.sable.api.physics.force.ForceGroup(
                                net.minecraft.network.chat.Component.translatable("force.create_submarine.stress"),
                                net.minecraft.network.chat.Component.translatable("force.create_submarine.stress.desc"),
                                0xFFFF4444,
                                true));
                net.minecraft.core.Registry.register(
                        dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY,
                        StressForceFeedSystem.INTERNAL_STRESS_ID,
                        new dev.ryanhcode.sable.api.physics.force.ForceGroup(
                                net.minecraft.network.chat.Component.translatable("force.create_submarine.internal_stress"),
                                net.minecraft.network.chat.Component.translatable("force.create_submarine.internal_stress.desc"),
                                0xFFFFAA00,
                                false));
                net.minecraft.core.Registry.register(
                        dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY,
                        StressForceFeedSystem.BUOYANCY_ID,
                        new dev.ryanhcode.sable.api.physics.force.ForceGroup(
                                net.minecraft.network.chat.Component.translatable("force.create_submarine.buoyancy"),
                                net.minecraft.network.chat.Component.translatable("force.create_submarine.buoyancy.desc"),
                                0xFF44DDBB,
                                true));
                modContainer.registerConfig(ModConfig.Type.COMMON, SubmarineConfig.SPEC);
                BLOCKS.register(modEventBus);
                ITEMS.register(modEventBus);
                BLOCK_ENTITIES.register(modEventBus);
                SOUNDS.register(modEventBus);
                MOB_EFFECTS.register(modEventBus);
                FLUID_TYPES.register(modEventBus);
                FLUIDS.register(modEventBus);
                MENUS.register(modEventBus);
                modEventBus.addListener(this::onCommonSetup);
                modEventBus.addListener(this::registerPayloads);
                NeoForge.EVENT_BUS.addListener(SubmarinePressureSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(SubmarineSinkingSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(SubmarineInteractionSystem::onServerTick);
                StressForceFeedSystem.register();
                StressForceFeedSystem.registerListeners();
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.stress.SubLevelStressAnalyzer::onGlobalServerTick);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.system.WrenchRepairHandler::onRightClickBlock);
                NeoForge.EVENT_BUS.addListener(this::onBlockPlaceAboveSensor);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.system.SubmarineLifecycleHandler::onServerStopping);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.system.SubmarineLifecycleHandler::onLevelUnload);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.system.SubmarineLifecycleHandler::onPlayerLoggedIn);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.stress.StressCommand::register);

                modEventBus.addListener(this::registerCapabilities);

                if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                        CreateSubmarineClient.init(modEventBus, modContainer);
                }
        }


        private void onBlockPlaceAboveSensor(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
                net.minecraft.world.level.block.state.BlockState below = event.getLevel().getBlockState(event.getPos().below());
                if (below.is(ELECTROLYZER.get()) || below.is(OXYGENE_DIFFUSER.get())) {
                        event.setCanceled(true);
                }
        }


        private void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
                final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(MOD_ID);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload::handle);
                registrar.playToServer(
                                com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.ShapeVizPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.ShapeVizPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.ShapeVizPayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.StressCenterPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.StressCenterPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.StressCenterPayload::handle);
        }
        private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                BALLAST_TANK_BE.get(),
                                (be, side) -> be.getClusterFluidHandler(side));
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                BALLAST_VENT_BE.get(),
                                (be, side) -> be.getFluidHandlerForSide(side));
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                ELECTROLYZER_BE.get(),
                                (be, side) -> be.combinedFluidHandler);
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                OXYGENE_DIFFUSER_BE.get(),
                                (be, side) -> be.oxygenTank);
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                WATER_THRUSTER_BE.get(),
                                (be, side) -> {
                                        if (side == null || side == be.getBlockState().getValue(net.minecraft.world.level.block.DirectionalBlock.FACING).getOpposite()) {
                                                return be.waterTank;
                                        }
                                        return null;
                                });
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                ELECTROLYZER_BE.get(),
                                (be, side) -> {
                                        if (side != null && side != Direction.UP && side != Direction.DOWN) return be.energyStorage;
                                        return null;
                                });
        }
        private void onCommonSetup(FMLCommonSetupEvent event) {
                event.enqueueWork(() -> {
                        HullStrengthConfig.load();
                        registerToSimulatedTab();
                });
        }
        @SuppressWarnings("unchecked")
        private void registerToSimulatedTab() {
                try {
                        Class<?> regClass = Class
                                        .forName("dev.simulated_team.simulated.registrate.SimulatedRegistrate");
                        List<Supplier<Item>> tabItems = (List<Supplier<Item>>) regClass.getField("TAB_ITEMS").get(null);
                        Map<ResourceLocation, ResourceLocation> itemToSection = (Map<ResourceLocation, ResourceLocation>) regClass
                                        .getField("ITEM_TO_SECTION").get(null);
                        tabItems.add(CREATIVE_OXYGENATOR_ITEM::get);
                        tabItems.add(BALLAST_TANK_ITEM::get);
                        tabItems.add(BALLAST_VENT_ITEM::get);
                        tabItems.add(OXYGENE_DIFFUSER_ITEM::get);
                        ResourceLocation subSection = ResourceLocation.fromNamespaceAndPath(MOD_ID, "submarine");
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "creative_oxygenator"), subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ballast_tank"), subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ballast_vent"), subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "oxygene_diffuser"),
                                        subSection);
                        tabItems.add(ELECTROLYZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "electrolyzer"), subSection);
                        tabItems.add(WATER_THRUSTER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "water_thruster"), subSection);
                        tabItems.add(GLASS_PRESSURIZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "glass_pressurizer"), subSection);
                        tabItems.add(FLOATER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "floater"), subSection);
                } catch (Exception ignored) {
                }
        }
}
