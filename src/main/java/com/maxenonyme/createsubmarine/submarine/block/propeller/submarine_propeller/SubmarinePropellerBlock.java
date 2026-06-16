package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class SubmarinePropellerBlock extends BasePropellerBlock {
    public SubmarinePropellerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends BasePropellerBlockEntity> getBlockEntityType() {
        return com.maxenonyme.createsubmarine.CreateSubmarine.SUBMARINE_PROPELLER_BE.get();
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(net.minecraft.network.chat.Component.empty());
            com.maxenonyme.createsubmarine.submarine.block.BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.submarine_propeller.tooltip.summary", 0xEBC255);
        } else {
            tooltip.add(net.minecraft.network.chat.Component.translatable("create_submarine.tooltip.holdForInfo",
                net.minecraft.network.chat.Component.translatable("create_submarine.tooltip.keyShift").withStyle(net.minecraft.ChatFormatting.GRAY))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
