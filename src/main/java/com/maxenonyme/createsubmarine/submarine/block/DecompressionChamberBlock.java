package com.maxenonyme.createsubmarine.submarine.block;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.DecompressionChamberBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import com.mojang.serialization.MapCodec;

public class DecompressionChamberBlock extends DirectionalBlock implements EntityBlock {

    public static final MapCodec<DecompressionChamberBlock> CODEC = simpleCodec(DecompressionChamberBlock::new);

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public DecompressionChamberBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecompressionChamberBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return type == CreateSubmarine.DECOMPRESSION_CHAMBER_BE.get()
                ? (l, p, s, be) -> ((DecompressionChamberBlockEntity) be).tick()
                : null;
    }
}
