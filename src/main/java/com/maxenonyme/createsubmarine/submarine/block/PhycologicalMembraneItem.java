package com.maxenonyme.createsubmarine.submarine.block;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class PhycologicalMembraneItem extends Item {
    public PhycologicalMembraneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (Screen.hasShiftDown()) {
            tooltipComponents.add(Component.empty());
            BallastTankItem.addTranslatableLines(tooltipComponents, "item.create_submarine.phycological_membrane.tooltip.summary", 0xEBC255);
        } else {
            tooltipComponents.add(Component.translatable("create_submarine.tooltip.holdForInfo",
                Component.translatable("create_submarine.tooltip.keyShift").withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
