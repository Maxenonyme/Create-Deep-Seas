package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubmarineWaterCullBuffer {
    private static final double POSE_MOVE_THRESHOLD_SQ = 0.25;
    private static final double DEFAULT_RADIUS = 16.0;

    private static final Map<UUID, WaterOcclusionRegion> regions = new HashMap<>();
    private static final Map<UUID, Vector3d> lastClientPose = new ConcurrentHashMap<>();
    private static boolean renderingSubmarineFluid = false;

    private SubmarineWaterCullBuffer() {
    }

    public static boolean isRenderingSubmarineFluid() {
        return renderingSubmarineFluid;
    }

    public static void beginSubmarineFluidRender() {
        renderingSubmarineFluid = true;
    }

    public static void endSubmarineFluidRender() {
        renderingSubmarineFluid = false;
    }

    public static void clearSodiumPoseCache(UUID id) {
        lastClientPose.remove(id);
    }

    public static void invalidateAllPoseCaches() {
        lastClientPose.clear();
    }

    public static void syncSubmarinePoses() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null)
            return;

        for (UUID id : regions.keySet()) {
            SubLevel sub = container.getSubLevel(id);
            if (sub == null)
                continue;
            Vector3dc p = sub.logicalPose().position();
            Vector3d last = lastClientPose.get(id);
            if (last != null && last.distanceSquared(p) < POSE_MOVE_THRESHOLD_SQ)
                continue;

            double r = computeRadius(id, sub);
            AABB newAABB = new AABB(p.x() - r, p.y() - r, p.z() - r, p.x() + r, p.y() + r, p.z() + r);
            AABB oldAABB = CompartmentTracker.getWorldAABB(id);
            CompartmentTracker.setWorldAABB(id, newAABB);

            if (mc.levelRenderer != null) {
                if (oldAABB != null)
                    invalidateSections(mc, oldAABB);
                invalidateSections(mc, newAABB);
            }

            if (last == null)
                lastClientPose.put(id, new Vector3d(p));
            else
                last.set(p);
        }
    }

    private static double computeRadius(UUID id, SubLevel sub) {
        Vector3d dims = CompartmentTracker.getCachedDimensions(id);
        if (dims != null)
            return Math.max(dims.x, Math.max(dims.y, dims.z)) * 0.75;
        try {
            BoundingBox3dc bb = sub.boundingBox();
            if (bb != null) {
                double sx = bb.maxX() - bb.minX();
                double sy = bb.maxY() - bb.minY();
                double sz = bb.maxZ() - bb.minZ();
                return Math.max(sx, Math.max(sy, sz)) * 0.75;
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_RADIUS;
    }

    private static void invalidateSections(Minecraft mc, AABB aabb) {
        int minSx = ((int) Math.floor(aabb.minX)) >> 4;
        int maxSx = ((int) Math.ceil(aabb.maxX)) >> 4;
        int minSy = ((int) Math.floor(aabb.minY)) >> 4;
        int maxSy = ((int) Math.ceil(aabb.maxY)) >> 4;
        int minSz = ((int) Math.floor(aabb.minZ)) >> 4;
        int maxSz = ((int) Math.ceil(aabb.maxZ)) >> 4;
        for (int sx = minSx; sx <= maxSx; sx++) {
            for (int sy = minSy; sy <= maxSy; sy++) {
                for (int sz = minSz; sz <= maxSz; sz++) {
                    mc.levelRenderer.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    public static void updateSubmarineOcclusion(UUID id, Collection<BlockPos> blocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        mc.execute(() -> {
            WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
            if (container == null)
                return;
            WaterOcclusionRegion old = regions.remove(id);
            if (old != null)
                container.removeRegion(old);
            if (blocks == null || blocks.isEmpty())
                return;

            Collection<BlockPos> filtered = filterToCubes(mc.level, id, blocks);
            if (filtered.isEmpty())
                return;

            BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(filtered);
            if (volume == null)
                return;
            WaterOcclusionRegion region = container.addRegion(volume);
            if (region != null)
                regions.put(id, region);
        });
    }

    private static Collection<BlockPos> filterToCubes(Level level, UUID id, Collection<BlockPos> blocks) {
        SubLevelContainer subContainer = SubLevelContainer.getContainer(level);
        if (subContainer == null)
            return blocks;
        SubLevel sub = subContainer.getSubLevel(id);
        if (sub == null)
            return blocks;
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return blocks;

        List<BlockPos> out = new ArrayList<>(blocks.size());
        ChunkPos lastCp = null;
        LevelChunk lastChunk = null;
        for (BlockPos pos : blocks) {
            ChunkPos cp = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
            if (!cp.equals(lastCp)) {
                lastCp = cp;
                lastChunk = plot.getChunk(plot.toLocal(cp));
            }
            if (lastChunk == null) {
                out.add(pos);
                continue;
            }
            BlockState state = lastChunk.getBlockState(pos);
            if (state.isAir() || state.isCollisionShapeFullBlock(lastChunk, pos))
                out.add(pos);
        }
        return out;
    }
}
