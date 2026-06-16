package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.content.blocks.propeller.small.SimplePropellerRenderer;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlock.REVERSED;

public class SubmarinePropellerRenderer extends SimplePropellerRenderer<SubmarinePropellerBlockEntity> {

    public SubmarinePropellerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PartialModel getCurrentModel(final SubmarinePropellerBlockEntity be) {
        return be.getBlockState().getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED : AllPartialModels.SUBMARINE_PROPELLER;
    }

    public PartialModel getContraModel(final SubmarinePropellerBlockEntity be) {
        return be.getBlockState().getValue(REVERSED) ? AllPartialModels.SUBMARINE_PROPELLER_REVERSED_CONTRA : AllPartialModels.SUBMARINE_PROPELLER_CONTRA;
    }

    @Override
    public float getAngle(final float partialTicks, final Direction dir, final SubmarinePropellerBlockEntity be) {
        return super.getAngle(partialTicks, dir, be) + getRotationOffsetForPosition(be, be.getBlockPos(), dir.getAxis());
    }

    @Override
    public void renderSafe(final SubmarinePropellerBlockEntity be, final float partialTicks, final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        if (dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final BlockState state = be.getBlockState();
        final Direction dir = state.getValue(BlockStateProperties.FACING);
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        final net.createmod.catnip.render.SuperByteBuffer propeller = net.createmod.catnip.render.CachedBuffers.partialFacing(this.getCurrentModel(be), state);
        final float angle = this.getAngle(partialTicks, dir, be);
        kineticRotationTransform(propeller, be, dir.getAxis(), angle, light);

        if (dir.getAxis().isHorizontal()) {
            propeller.rotateCentered(net.createmod.catnip.math.AngleHelper.rad(net.createmod.catnip.math.AngleHelper.horizontalAngle(dir.getOpposite())), Direction.UP);
        }
        if (dir.getAxis().isVertical()) {
            propeller.rotateCentered(net.createmod.catnip.math.AngleHelper.rad(net.createmod.catnip.math.AngleHelper.verticalAngle(dir.getOpposite())), Direction.EAST);
        }
        propeller.translate(0, 0, -3 / 16f).rotateCentered(net.createmod.catnip.math.AngleHelper.rad(-90 - net.createmod.catnip.math.AngleHelper.verticalAngle(dir)), Direction.EAST);
        propeller.renderInto(ms, vb);

        final net.createmod.catnip.render.SuperByteBuffer contraPropeller = net.createmod.catnip.render.CachedBuffers.partialFacing(this.getContraModel(be), state);
        kineticRotationTransform(contraPropeller, be, dir.getAxis(), -angle, light);

        if (dir.getAxis().isHorizontal()) {
            contraPropeller.rotateCentered(net.createmod.catnip.math.AngleHelper.rad(net.createmod.catnip.math.AngleHelper.horizontalAngle(dir.getOpposite())), Direction.UP);
        }
        if (dir.getAxis().isVertical()) {
            contraPropeller.rotateCentered(net.createmod.catnip.math.AngleHelper.rad(net.createmod.catnip.math.AngleHelper.verticalAngle(dir.getOpposite())), Direction.EAST);
        }
        contraPropeller.translate(0, 0, -3 / 16f).rotateCentered(net.createmod.catnip.math.AngleHelper.rad(-90 - net.createmod.catnip.math.AngleHelper.verticalAngle(dir)), Direction.EAST);
        contraPropeller.renderInto(ms, vb);
    }
}
