package com.maxenonyme.highseas;

import com.maxenonyme.highseas.sail.SailWindSystem;
import com.maxenonyme.highseas.system.HighSeasLifecycleHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateHighSeas.MOD_ID)
public class CreateHighSeas {
    public static final String MOD_ID = "create_high_seas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateHighSeas(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(SailWindSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(HighSeasLifecycleHandler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(HighSeasLifecycleHandler::onLevelUnload);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.maxenonyme.highseas.client.SailRenderClient.init(modEventBus);
        }
    }
}
