package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.submarine.util.SteelCableHolderAccessor;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.items.rope.RopeItem.RopeItem;
import dev.simulated_team.simulated.data.advancements.SimAdvancements;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SteelCableItem extends RopeItem {

    public SteelCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        Level level = context.getLevel();
        ItemStack heldStack = context.getItemInHand();
        Player player = context.getPlayer();

        boolean validLocation = isValidRopeAttachment(level, clickedPos);

        if (player != null && player.isShiftKeyDown()) {
            heldStack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
            return InteractionResult.SUCCESS;
        }

        if (validLocation) {
            if (heldStack.has(SimDataComponents.ROPE_FIRST_CONNECTION)) {
                if (!level.isClientSide) {
                    if (!this.attachSteelCable(level, heldStack.get(SimDataComponents.ROPE_FIRST_CONNECTION), clickedPos)) {
                        heldStack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
                        return InteractionResult.SUCCESS;
                    } else {
                        SimAdvancements.LEARNING_THE_ROPES.awardTo(player);
                    }
                }
                heldStack.remove(SimDataComponents.ROPE_FIRST_CONNECTION);
                if (!player.isCreative()) {
                    context.getItemInHand().shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
            heldStack.set(SimDataComponents.ROPE_FIRST_CONNECTION, clickedPos);
            return InteractionResult.SUCCESS;
        }

        return super.useOn(context);
    }

    private boolean attachSteelCable(Level level, BlockPos posA, BlockPos posB) {
        RopeStrandHolderBehavior ropeHolderA = getRopeHolder(level, posA);
        if (ropeHolderA == null) return false;

        RopeStrandHolderBehavior ropeHolderB = getRopeHolder(level, posB);
        if (ropeHolderB == null) return false;

        if (ropeHolderB.blockEntity instanceof RopeWinchBlockEntity && !(ropeHolderA.blockEntity instanceof RopeWinchBlockEntity)) {
            RopeStrandHolderBehavior temp = ropeHolderA;
            ropeHolderA = ropeHolderB;
            ropeHolderB = temp;
        }
        if (ropeHolderA.blockEntity instanceof RopeWinchBlockEntity && ropeHolderB.blockEntity instanceof RopeWinchBlockEntity) {
            return false;
        }

        if (ropeHolderA instanceof SteelCableHolderAccessor accessorA) {
            accessorA.createsubmarine$setSteelCable(true);
        }
        if (ropeHolderB instanceof SteelCableHolderAccessor accessorB) {
            accessorB.createsubmarine$setSteelCable(true);
        }

        if (ropeHolderA.createRope(ropeHolderB)) {
            ropeHolderA.blockEntity.notifyUpdate();
            ropeHolderB.blockEntity.notifyUpdate();
            level.playSound(null, posA, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
            level.playSound(null, posB, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
            return true;
        }
        if (ropeHolderA instanceof SteelCableHolderAccessor accessorA) {
            accessorA.createsubmarine$setSteelCable(false);
        }
        if (ropeHolderB instanceof SteelCableHolderAccessor accessorB) {
            accessorB.createsubmarine$setSteelCable(false);
        }
        return false;
    }
}
