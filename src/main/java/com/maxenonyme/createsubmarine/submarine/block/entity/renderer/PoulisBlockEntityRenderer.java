package com.maxenonyme.createsubmarine.submarine.block.entity.renderer;

import com.maxenonyme.createsubmarine.submarine.block.PoulisBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.PoulisBlockEntity;
import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public class PoulisBlockEntityRenderer implements BlockEntityRenderer<PoulisBlockEntity> {
    private static final float PIVOT_X = 0.875f;
    private static final float PIVOT_Y = 1.125f;
    private static final float PIVOT_Z = 0.506f;
    private static final double WHEEL_RADIUS = 0.6875;
    private static final double MAX_FRAME_SECONDS = 0.1;

    public PoulisBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PoulisBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(PoulisBlock.FACING)) return;

        advanceWheel(be);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(facingRotation(state.getValue(PoulisBlock.FACING)));
        ms.translate(-0.5, -0.5, -0.5);
        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.ZP.rotation(be.clientWheelAngle));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);

        float heat = be.getHeat();
        int r = 255;
        int g = (int) (255 * (1.0f - heat));
        int b = (int) (255 * (1.0f - heat));
        int color = (255 << 24) | (r << 16) | (g << 8) | b;

        CachedBuffers.partial(AllPartialModels.POULIS_CORE, state)
                .light(light)
                .color(color)
                .renderInto(ms, buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)));

        ms.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(PoulisBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }

    private void advanceWheel(PoulisBlockEntity be) {
        long now = System.nanoTime();
        long last = be.clientLastNanos;
        be.clientLastNanos = now;

        BlockState state = be.getBlockState();
        if (!state.hasProperty(PoulisBlock.CONNECTED) || !state.getValue(PoulisBlock.CONNECTED)) return;

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(be);
        if (sub == null || last == 0L) return;

        double dx = sub.logicalPose().position().x() - sub.lastPose().position().x();
        double dy = sub.logicalPose().position().y() - sub.lastPose().position().y();
        double dz = sub.logicalPose().position().z() - sub.lastPose().position().z();

        org.joml.Vector3d localUp = new org.joml.Vector3d(0, 1, 0);
        sub.logicalPose().orientation().transform(localUp);
        double signedSlide = dx * localUp.x + dy * localUp.y + dz * localUp.z;

        double frameSeconds = Math.min((now - last) / 1_000_000_000.0, MAX_FRAME_SECONDS);
        be.clientWheelAngle -= (float) (signedSlide * 20.0 / WHEEL_RADIUS * frameSeconds);
    }

    private static Quaternionf facingRotation(Direction facing) {
        int x = 0;
        int y = 0;
        switch (facing) {
            case SOUTH -> y = 180;
            case EAST -> y = 90;
            case WEST -> y = 270;
            case DOWN -> x = 180;
            default -> { }
        }
        return new Quaternionf().rotateYXZ((float) Math.toRadians(-y), (float) Math.toRadians(-x), 0f);
    }
}
