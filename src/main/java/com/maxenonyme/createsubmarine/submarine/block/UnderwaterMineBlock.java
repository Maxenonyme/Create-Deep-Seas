package com.maxenonyme.createsubmarine.submarine.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.UnderwaterMineBlockEntity;

public class UnderwaterMineBlock extends Block implements EntityBlock {

    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(-1.0, 0.0, -1.0, 17.0, 18.0, 17.0);

    public UnderwaterMineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltip.add(net.minecraft.network.chat.Component.empty());
            com.maxenonyme.createsubmarine.submarine.block.BallastTankItem.addTranslatableLines(tooltip, "item.create_submarine.underwater_mine.tooltip.summary", 0xEBC255);
        } else {
            tooltip.add(net.minecraft.network.chat.Component.translatable("create_submarine.tooltip.holdForInfo",
                net.minecraft.network.chat.Component.translatable("create_submarine.tooltip.keyShift").withStyle(net.minecraft.ChatFormatting.GRAY))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UnderwaterMineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof UnderwaterMineBlockEntity umbe) UnderwaterMineBlockEntity.serverTick(l, p, umbe);
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        if (placer != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof UnderwaterMineBlockEntity mine) {
                mine.ownerUUID = placer.getUUID();
            }
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }
}
