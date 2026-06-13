package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class SubLevelCrackRenderer {
    private static final Map<UUID, Map<BlockPos, int[]>> CLIENT_CRACKS = new ConcurrentHashMap<>();

    public static void updateCrack(UUID subId, BlockPos plotPos, int crackLevel, int blockId) {
        if (crackLevel < 0) {
            Map<BlockPos, int[]> m = CLIENT_CRACKS.get(subId);
            if (m != null) m.remove(plotPos);
        } else {
            CLIENT_CRACKS.computeIfAbsent(subId, k -> new ConcurrentHashMap<>())
                    .put(plotPos, new int[]{crackLevel});
        }
    }

    public static void clearSub(UUID subId) {
        CLIENT_CRACKS.remove(subId);
    }

    public static void clearAll() {
        CLIENT_CRACKS.clear();
        com.maxenonyme.createsubmarine.submarine.client.renderer.SubLevelRenderPoseCapture.clearAll();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (CLIENT_CRACKS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        for (Map.Entry<UUID, Map<BlockPos, int[]>> subEntry : CLIENT_CRACKS.entrySet()) {
            UUID subId = subEntry.getKey();
            Map<BlockPos, int[]> cracks = subEntry.getValue();
            if (cracks.isEmpty()) continue;

            SubLevelAccess sub = CompartmentTracker.getSub(subId);
            if (sub == null) continue;

            dev.ryanhcode.sable.companion.math.Pose3dc renderPose =
                    com.maxenonyme.createsubmarine.submarine.client.renderer.SubLevelRenderPoseCapture.get(subId);
            if (renderPose == null) {
                if (sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel clientSub) {
                    renderPose = clientSub.renderPose();
                } else {
                    renderPose = sub.logicalPose();
                }
            }

            if (renderPose == null) continue;
            org.joml.Quaterniondc subRot = renderPose.orientation();

            for (Map.Entry<BlockPos, int[]> crackEntry : cracks.entrySet()) {
                BlockPos plotPos = crackEntry.getKey();
                int crackLevel = crackEntry.getValue()[0];

                int stage = 0;
                if (crackLevel == 1) stage = 2;
                else if (crackLevel == 2) stage = 5;
                else if (crackLevel >= 3) stage = 8;
                ResourceLocation tex = ResourceLocation.withDefaultNamespace(
                        "textures/block/destroy_stage_" + stage + ".png");

                BlockState state = mc.level.getBlockState(plotPos);
                if (state.isAir()) continue;

                org.joml.Vector3d worldVec = new org.joml.Vector3d(plotPos.getX(), plotPos.getY(), plotPos.getZ());
                renderPose.transformPosition(worldVec);

                poseStack.pushPose();
                poseStack.translate(worldVec.x - camPos.x, worldVec.y - camPos.y, worldVec.z - camPos.z);
                poseStack.mulPose(new org.joml.Quaternionf(
                        (float) subRot.x(), (float) subRot.y(), (float) subRot.z(), (float) subRot.w()));

                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(1.01f, 1.01f, 1.01f);
                poseStack.translate(-0.5f, -0.5f, -0.5f);

                VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
                renderCube(poseStack.last().pose(), consumer);

                poseStack.popPose();
            }
        }

        for (int s = 0; s <= 9; s++) {
            ResourceLocation tex = ResourceLocation.withDefaultNamespace(
                    "textures/block/destroy_stage_" + s + ".png");
            bufferSource.endBatch(RenderType.entityTranslucent(tex));
        }
    }

    private static void renderCube(Matrix4f mat, VertexConsumer v) {
        quad(v, mat, 0,0,1, 1,0,1, 1,0,0, 0,0,0,  0,-1,0);
        quad(v, mat, 0,1,0, 1,1,0, 1,1,1, 0,1,1,  0,1,0);
        quad(v, mat, 1,1,0, 0,1,0, 0,0,0, 1,0,0,  0,0,-1);
        quad(v, mat, 0,1,1, 1,1,1, 1,0,1, 0,0,1,  0,0,1);
        quad(v, mat, 0,1,0, 0,1,1, 0,0,1, 0,0,0,  -1,0,0);
        quad(v, mat, 1,1,1, 1,1,0, 1,0,0, 1,0,1,  1,0,0);
    }

    private static void quad(VertexConsumer v, Matrix4f mat,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz) {
        vert(v, mat, x3, y3, z3, 0, 1, nx, ny, nz);
        vert(v, mat, x2, y2, z2, 1, 1, nx, ny, nz);
        vert(v, mat, x1, y1, z1, 1, 0, nx, ny, nz);
        vert(v, mat, x0, y0, z0, 0, 0, nx, ny, nz);
    }

    private static void vert(VertexConsumer v, Matrix4f mat,
            float x, float y, float z, float u, float w,
            float nx, float ny, float nz) {
        v.addVertex(mat, x, y, z)
                .setColor(-1)
                .setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
