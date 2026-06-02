package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import com.maxenonyme.createsubmarine.submarine.block.entity.BallastVentBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class BallastVentRenderer extends KineticBlockEntityRenderer<BallastVentBlockEntity> {
    public BallastVentRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BallastVentBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();

        CachedBuffers.block(state)
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));

        if (be.getLevel() != null && !be.getLevel().getClass().getSimpleName().contains("Ponder")) {
            super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        }
    }

    @Override
    protected SuperByteBuffer getRotatedModel(BallastVentBlockEntity be, BlockState state) {
        return CachedBuffers.partial(com.simibubi.create.AllPartialModels.SHAFT_HALF, state);
    }
}