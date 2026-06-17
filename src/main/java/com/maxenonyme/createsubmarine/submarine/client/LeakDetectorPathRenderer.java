package com.maxenonyme.createsubmarine.submarine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class LeakDetectorPathRenderer {

    private static final Map<UUID, PathData> PATHS = new ConcurrentHashMap<>();

    private static record PathData(List<Vec3> waypoints, long timestamp) {}

    private static final long LINE_DURATION_MS = 6000;

    public static void setPath(UUID subId, List<Vec3> waypoints) {
        PATHS.put(subId, new PathData(new ArrayList<>(waypoints), System.currentTimeMillis()));
    }

    public static void clearPath(UUID subId) {
        PATHS.remove(subId);
    }

    public static void clearAll() {
        PATHS.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (PATHS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(4f);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        PATHS.entrySet().removeIf(entry -> {
            PathData data = entry.getValue();
            if (now - data.timestamp > LINE_DURATION_MS) return true;

            List<Vec3> wps = data.waypoints;
            if (wps.size() < 2) return false;

            float age = (now - data.timestamp) / (float) LINE_DURATION_MS;
            float alpha = 1.0f - age;

            for (int i = 0; i < wps.size() - 1; i++) {
                Vec3 from = wps.get(i);
                Vec3 to = wps.get(i + 1);

                double distSq = to.distanceToSqr(camPos);
                if (distSq > 6400) continue;

                poseStack.pushPose();
                poseStack.translate(from.x - camPos.x, from.y - camPos.y, from.z - camPos.z);
                Matrix4f mat = poseStack.last().pose();

                double dx = to.x - from.x;
                double dy = to.y - from.y;
                double dz = to.z - from.z;

                float r = 0.3f + alpha * 0.4f;
                float g = 0.6f + alpha * 0.3f;
                float b = 1.0f;

                consumer.addVertex(mat, 0f, 0f, 0f).setColor(r, g, b, alpha).setNormal(0, 0, 0);
                consumer.addVertex(mat, (float) dx, (float) dy, (float) dz).setColor(r, g, b, alpha).setNormal(0, 0, 0);
                poseStack.popPose();
            }

            return false;
        });

        bufferSource.endBatch(RenderType.lines());
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }
}
