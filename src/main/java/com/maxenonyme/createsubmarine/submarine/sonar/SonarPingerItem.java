package com.maxenonyme.createsubmarine.submarine.sonar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SonarPingerItem extends Item {

    public SonarPingerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Direction face = ctx.getClickedFace();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        BlockPos pos = ctx.getClickedPos().relative(face);

        if (player != null && !player.mayUseItemAt(pos, face, stack)) {
            return InteractionResult.FAIL;
        }

        Level world = ctx.getLevel();
        var entity = new SonarPingerEntity(world, pos, face);

        if (!entity.survives()) {
            return InteractionResult.CONSUME;
        }

        if (!world.isClientSide) {
            entity.playPlacementSound();
            world.addFreshEntity(entity);
        }

        stack.shrink(1);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
