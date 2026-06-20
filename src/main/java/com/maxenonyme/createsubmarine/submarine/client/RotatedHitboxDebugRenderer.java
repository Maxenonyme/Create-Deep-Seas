package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.submarine.math.RotatedEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.entity.PartEntity;
import org.joml.Matrix4f;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RotatedHitboxDebugRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.getEntityRenderDispatcher().shouldRenderHitBoxes()) return;

        Vec3 camPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f proj = event.getProjectionMatrix();

        RenderSystem.lineWidth(2f);

        for (Entity entity : mc.level.entitiesForRendering()) {
            // Check multipart entity parts (parts are NOT in entity list)
            if (entity.isMultipartEntity()) {
                for (PartEntity<?> part : entity.getParts()) {
                    if (part instanceof RotatedEntity rotated) {
                        drawPartAABBs(consumer, proj, camPos, rotated);
                    }
                }
            }
            // Check direct RotatedEntity (e.g. CookiecutterShark)
            if (entity instanceof RotatedEntity rotated) {
                drawPartAABBs(consumer, proj, camPos, rotated);
            }
        }

        bufferSource.endBatch(RenderType.lines());
        RenderSystem.lineWidth(1f);
    }

    private static void drawPartAABBs(VertexConsumer consumer, Matrix4f proj, Vec3 camPos, RotatedEntity rotated) {
        Vec3 entityCam = ((Entity) rotated).position().subtract(camPos);
        if (entityCam.lengthSqr() > 4096) return;
        for (AABB aabb : rotated.getRotatedHitbox().getParts()) {
            Vec3 cam = new Vec3(
                (aabb.minX + aabb.maxX) * 0.5 - camPos.x,
                (aabb.minY + aabb.maxY) * 0.5 - camPos.y,
                (aabb.minZ + aabb.maxZ) * 0.5 - camPos.z);
            PoseStack local = new PoseStack();
            local.translate(cam.x, cam.y, cam.z);
            Matrix4f mvp = new Matrix4f(proj);
            mvp.mul(local.last().pose());
            drawAABB(consumer, mvp, aabb);
        }
    }

    private static void drawAABB(VertexConsumer v, Matrix4f mat, AABB aabb) {
        double cx = (aabb.minX + aabb.maxX) * 0.5;
        double cy = (aabb.minY + aabb.maxY) * 0.5;
        double cz = (aabb.minZ + aabb.maxZ) * 0.5;
        float hx = (float) (aabb.maxX - cx);
        float hy = (float) (aabb.maxY - cy);
        float hz = (float) (aabb.maxZ - cz);

        float x0 = -hx, y0 = -hy, z0 = -hz;
        float x1 = hx, y1 = hy, z1 = hz;

        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float[][] verts = {
            {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
            {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}
        };

        for (int[] e : edges) {
            line(v, mat, verts[e[0]][0], verts[e[0]][1], verts[e[0]][2],
                      verts[e[1]][0], verts[e[1]][1], verts[e[1]][2],
                      0.2f, 0.8f, 1.0f);
        }
    }

    private static void line(VertexConsumer v, Matrix4f mat,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float r, float g, float b) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6f) { dx /= len; dy /= len; dz /= len; }
        v.addVertex(mat, x0, y0, z0).setColor(r, g, b, 1f).setNormal(dx, dy, dz);
        v.addVertex(mat, x1, y1, z1).setColor(r, g, b, 1f).setNormal(dx, dy, dz);
    }
}
