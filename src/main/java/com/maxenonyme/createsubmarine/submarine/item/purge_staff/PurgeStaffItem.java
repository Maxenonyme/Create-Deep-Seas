package com.maxenonyme.createsubmarine.submarine.item.purge_staff;

import java.util.function.Consumer;

import com.maxenonyme.createsubmarine.submarine.client.renderer.PurgeStaffItemRenderer;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class PurgeStaffItem extends Item {

    public PurgeStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new PurgeStaffItemRenderer()));
    }
}
