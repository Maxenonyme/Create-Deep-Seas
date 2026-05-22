package com.maxenonyme.createsubmarine;

import com.maxenonyme.createsubmarine.submarine.block.entity.renderer.ElectrolyzerBlockEntityRenderer;
import com.maxenonyme.createsubmarine.submarine.client.ShapeVizRenderer;
import com.maxenonyme.createsubmarine.submarine.client.SubLevelCrackRenderer;
import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import com.maxenonyme.createsubmarine.submarine.client.WatermarkOverlay;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerScreen;
import com.maxenonyme.createsubmarine.submarine.ponder.SubmarinePonderPlugin;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

// Say whallih bro say whallih :) idk the problem of server

public final class CreateSubmarineClient {
    private CreateSubmarineClient() {
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new ConfigurationScreen(container, parent));

        modEventBus.addListener(CreateSubmarineClient::onClientSetup);
        modEventBus.addListener(CreateSubmarineClient::onRegisterRenderers);
        modEventBus.addListener(CreateSubmarineClient::onRegisterScreens);
        modEventBus.addListener(WatermarkOverlay::register);

        NeoForge.EVENT_BUS.register(SubmarineFogHandler.class);
        NeoForge.EVENT_BUS.register(SubLevelCrackRenderer.class);
        NeoForge.EVENT_BUS.register(ShapeVizRenderer.class);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class,
            e -> ShapeVizRenderer.onClientTick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            SubLevelCrackRenderer.clearAll();
            ShapeVizRenderer.clearAll();
            SubLevelRegistry.clearAll();
            CompartmentTracker.clearAll();
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                CreateSubmarine.ELECTROLYZER_BE.get(),
                ElectrolyzerBlockEntityRenderer::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.ELECTROLYZER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.OXYGENE_DIFFUSER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.WATER_THRUSTER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateSubmarine.GLASS_PRESSURIZER.get(), RenderType.cutout());
        });

        PonderIndex.addPlugin(new SubmarinePonderPlugin());
        WaterOcclusionRenderer.setIsEnabled(true);
        AllPartialModels.init();
        SimpleBlockEntityVisualizer
                .builder(CreateSubmarine.BALLAST_VENT_BE.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(be -> true)
                .apply();
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(
                CreateSubmarine.ELECTROLYZER_MENU.get(),
                ElectrolyzerScreen::new);
    }
}
