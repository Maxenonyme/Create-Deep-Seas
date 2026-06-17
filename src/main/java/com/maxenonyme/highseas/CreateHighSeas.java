package com.maxenonyme.highseas;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CreateHighSeas.MOD_ID)
public class CreateHighSeas {
    public static final String MOD_ID = "create_high_seas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateHighSeas(IEventBus modEventBus, ModContainer modContainer) {
    }
}
