package com.maxenonyme.createsubmarine.submarine.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ArrestingHookItem extends BlockItem {
    public ArrestingHookItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("⚠ ")
                .append(Component.translatable("item.create_submarine.arresting_hook.tooltip.wip"))
                .withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
