package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ShapeVizRenderer {

    private static final Map<UUID, BlockPos> STRESS_CENTER_POS = new ConcurrentHashMap<>();
    private static final Set<UUID> STRESS_CENTER_ENABLED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<HullFace>> HULL_FACES = new ConcurrentHashMap<>();
    private static int particleSpawnCounter = 0;

    public static void setStressCenterEnabled(UUID subId, boolean enabled) {
        if (enabled) STRESS_CENTER_ENABLED.add(subId);
        else STRESS_CENTER_ENABLED.remove(subId);
    }

    public record HullFace(BlockPos worldPos, float nx, float ny, float nz, float stressColorR, float stressColorG, float stressColorB) {}

    public static void setStressCenter(UUID subId, BlockPos worldPos) {
        if (worldPos != null) STRESS_CENTER_POS.put(subId, worldPos);
        else STRESS_CENTER_POS.remove(subId);
    }

    public static void clearStressCenter(UUID subId) {
        STRESS_CENTER_POS.remove(subId);
    }

    public static void setHullFaces(UUID subId, List<HullFace> faces) {
        HULL_FACES.put(subId, faces);
    }

    public static void clearHullFaces(UUID subId) {
        HULL_FACES.remove(subId);
    }

    public static void clearAll() {
        STRESS_CENTER_POS.clear();
        HULL_FACES.clear();
    }

    public static void clearSub(UUID subId) {
        STRESS_CENTER_POS.remove(subId);
        HULL_FACES.remove(subId);
    }

    public static void spawnParticles() {
        if (HULL_FACES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)) return;
        Vec3 camPos = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        for (Map.Entry<UUID, List<HullFace>> entry : HULL_FACES.entrySet()) {
            for (HullFace face : entry.getValue()) {
                double dx = face.worldPos.getX() + 0.5 - camPos.x;
                double dy = face.worldPos.getY() + 0.5 - camPos.y;
                double dz = face.worldPos.getZ() + 0.5 - camPos.z;
                if (dx * dx + dy * dy + dz * dz > 6400) continue;
                Vector3f color = new Vector3f(face.stressColorR, face.stressColorG, face.stressColorB);
                level.addParticle(
                    new DustParticleOptions(color, 0.6f),
                    face.worldPos.getX() + 0.5 + face.nx * 0.5,
                    face.worldPos.getY() + 0.5 + face.ny * 0.5,
                    face.worldPos.getZ() + 0.5 + face.nz * 0.5,
                    0, 0, 0
                );
            }
        }
    }

    public static void spawnStressCenterParticles() {
        if (STRESS_CENTER_POS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)) return;
        Vec3 camPos = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        for (Map.Entry<UUID, BlockPos> entry : STRESS_CENTER_POS.entrySet()) {
            UUID id = entry.getKey();
            if (!STRESS_CENTER_ENABLED.contains(id)) continue;
            BlockPos sc = entry.getValue();
            if (sc == null) continue;
            double dx = sc.getX() + 0.5 - camPos.x;
            double dy = sc.getY() + 0.5 - camPos.y;
            double dz = sc.getZ() + 0.5 - camPos.z;
            if (dx * dx + dy * dy + dz * dz > 6400) continue;
            level.addParticle(
                new DustParticleOptions(new Vector3f(1f, 0.5f, 0f), 1.2f),
                sc.getX() + 0.5, sc.getY() + 0.5, sc.getZ() + 0.5,
                0, 0, 0
            );
        }
    }

    public static void onClientTick() {
        if (++particleSpawnCounter % 5 != 0) return;
        spawnParticles();
        spawnStressCenterParticles();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (STRESS_CENTER_POS.isEmpty() && HULL_FACES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(3f);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        for (Map.Entry<UUID, BlockPos> entry : STRESS_CENTER_POS.entrySet()) {
            UUID id = entry.getKey();
            if (!STRESS_CENTER_ENABLED.contains(id)) continue;
            BlockPos sc = entry.getValue();
            if (sc == null) continue;
            double dx = sc.getX() + 0.5 - camPos.x;
            double dy = sc.getY() + 0.5 - camPos.y;
            double dz = sc.getZ() + 0.5 - camPos.z;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist > 100) continue;
            poseStack.pushPose();
            poseStack.translate(sc.getX() + 0.5 - camPos.x, sc.getY() + 0.5 - camPos.y, sc.getZ() + 0.5 - camPos.z);
            Matrix4f mat = poseStack.last().pose();
            double arm = 0.7;
            line(consumer, mat, -arm, 0, 0, arm, 0, 0, 1f, 0f, 0f);
            line(consumer, mat, 0, -arm, 0, 0, arm, 0, 0f, 1f, 0f);
            line(consumer, mat, 0, 0, -arm, 0, 0, arm, 0f, 0f, 1f);
            double ringR = 0.3;
            int segments = 12;
            for (int i = 0; i < segments; i++) {
                double a1 = 2.0 * Math.PI * i / segments;
                double a2 = 2.0 * Math.PI * (i + 1) / segments;
                line(consumer, mat,
                    ringR * Math.cos(a1), ringR * Math.sin(a1), 0,
                    ringR * Math.cos(a2), ringR * Math.sin(a2), 0,
                    1f, 0.5f, 0f);
            }
            poseStack.popPose();
        }
        bufferSource.endBatch(RenderType.lines());

        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();

        spawnParticles();
    }

    private static void line(VertexConsumer v, Matrix4f mat,
                              double x0, double y0, double z0,
                              double x1, double y1, double z1,
                              float r, float g, float b) {
        v.addVertex(mat, (float)x0, (float)y0, (float)z0).setColor(r, g, b, 1f).setNormal(0, 0, 0);
        v.addVertex(mat, (float)x1, (float)y1, (float)z1).setColor(r, g, b, 1f).setNormal(0, 0, 0);
    }
}
