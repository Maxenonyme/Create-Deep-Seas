package com.maxenonyme.highseas.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.simulated_team.simulated.content.blocks.symmetric_sail.SymmetricSailBlock;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SymmetricSailBlock.class)
public class SymmetricSailCollisionMixin {

    @ModifyReturnValue(method = "getShape", at = @At("RETURN"))
    private VoxelShape createhighseas$bulgeCollision(VoxelShape original, BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
        if (axis == Direction.Axis.Y) {
            return original;
        }

        Direction plus = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        boolean supPlus = isSupport(level, pos.relative(plus));
        boolean supMinus = isSupport(level, pos.relative(plus.getOpposite()));

        double lo, hi;
        if (supMinus && !supPlus) {
            lo = 0.0;
            hi = 0.9;
        } else if (supPlus && !supMinus) {
            lo = 0.1;
            hi = 1.0;
        } else {
            lo = 0.1;
            hi = 0.9;
        }

        VoxelShape box = switch (axis) {
            case X -> Shapes.box(lo, 0.0, 0.0, hi, 1.0, 1.0);
            case Z -> Shapes.box(0.0, 0.0, lo, 1.0, 1.0, hi);
            default -> original;
        };
        return Shapes.or(original, box);
    }

    private static boolean isSupport(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(SimTags.Blocks.SYMMETRIC_SAILS);
    }
}
