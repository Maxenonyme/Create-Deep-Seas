package com.maxenonyme.createsubmarine.submarine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ShapeVizRenderer {

    private static final Map<UUID, List<HullFace>> HULL_FACES = new ConcurrentHashMap<>();
    private static int particleSpawnCounter = 0;

    public record HullFace(BlockPos worldPos, float nx, float ny, float nz, float stressColorR, float stressColorG, float stressColorB) {}

    public static void setHullFaces(UUID subId, List<HullFace> faces) {
        HULL_FACES.put(subId, faces);
    }

    public static void clearHullFaces(UUID subId) {
        HULL_FACES.remove(subId);
    }

    public static void clearAll() {
        HULL_FACES.clear();
    }

    public static void clearSub(UUID subId) {
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

    public static void onClientTick() {
        if (++particleSpawnCounter % 5 != 0) return;
        spawnParticles();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (HULL_FACES.isEmpty()) return;

        spawnParticles();
    }
}
