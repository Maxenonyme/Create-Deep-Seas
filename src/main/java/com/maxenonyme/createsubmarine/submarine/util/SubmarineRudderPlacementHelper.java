package com.maxenonyme.createsubmarine.submarine.util;

import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.function.Predicate;

public class SubmarineRudderPlacementHelper implements IPlacementHelper {

    private final Predicate<ItemStack> itemPredicate;
    private final Predicate<BlockState> statePredicate;

    public SubmarineRudderPlacementHelper(Predicate<ItemStack> itemPredicate, Predicate<BlockState> statePredicate) {
        this.itemPredicate = itemPredicate;
        this.statePredicate = statePredicate;
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return this.itemPredicate;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return this.statePredicate;
    }

    @Override
    public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
        Direction.Axis axis;
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            axis = state.getValue(BlockStateProperties.AXIS);
        } else {
            return PlacementOffset.fail();
        }

        List<Direction> validDir = IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getLocation(), axis);
        for (Direction dir : validDir) {
            if (!world.getBlockState(pos.relative(dir)).canBeReplaced())
                continue;

            return PlacementOffset.success(pos.relative(dir), s -> s.setValue(BlockStateProperties.AXIS, axis));
        }

        return PlacementOffset.fail();
    }
}
