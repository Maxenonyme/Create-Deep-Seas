package com.maxenonyme.AbyssDimension.block;

import com.maxenonyme.AbyssDimension.block.entity.SubmarineLianaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SubmarineLianaBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(7.5, 0.0, 7.5, 8.5, 16.0, 8.5);

    public SubmarineLianaBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WATER)
                .instabreak()
                .sound(SoundType.WET_GRASS)
                .noOcclusion()
                .isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false)
                .pushReaction(PushReaction.DESTROY));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SubmarineLianaBlockEntity lianaBe) {
            return lianaBe.getWorldLightLevel();
        }
        return 10;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SubmarineLianaBlockEntity(pos, state);
    }

}
