package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SubmarineWaterCullBuffer;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = CreateHighSeas.MOD_ID, value = Dist.CLIENT)
public final class BoatWaterCulling {

    private static final int UPDATE_INTERVAL_TICKS = 40;
    private static final long MAX_SCAN_CELLS = 4_000_000L;

    private static final Set<UUID> TRACKED_IDS = new HashSet<>();
    private static final Map<UUID, Set<BlockPos>> CACHED_OCCLUDERS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SCAN_TICK = new HashMap<>();

    private BoatWaterCulling() {
    }

    public static boolean isEnabled() {
        try {
            return SubmarineConfig.ENABLE_BOAT_WATER_CULLING.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.level == null) {
            clearAllState();
            return;
        }
        updateRegions(mc.level);
    }

    private static void updateRegions(Level level) {
        SubLevelContainer subContainer;
        try {
            subContainer = SubLevelContainer.getContainer(level);
        } catch (Throwable t) {
            return;
        }
        if (subContainer == null)
            return;

        long now = level.getGameTime();
        List<? extends SubLevel> allSubs = subContainer.getAllSubLevels();
        Set<UUID> seenIds = new HashSet<>();

        for (SubLevel sub : allSubs) {
            UUID id = sub.getUniqueId();
            if (id == null)
                continue;
            seenIds.add(id);

            if (!isBoat(id) || !isFloating(sub)) {
                if (TRACKED_IDS.contains(id))
                    deactivate(id);
                continue;
            }

            Long lastScan = LAST_SCAN_TICK.get(id);
            Set<BlockPos> occluders = CACHED_OCCLUDERS.get(id);
            boolean needRescan = lastScan == null || occluders == null || (now - lastScan) >= UPDATE_INTERVAL_TICKS;

            if (needRescan) {
                occluders = collectOccluders(sub);
                CACHED_OCCLUDERS.put(id, occluders);
                LAST_SCAN_TICK.put(id, now);
            } else if (TRACKED_IDS.contains(id)) {
                continue;
            }

            if (occluders.isEmpty()) {
                if (TRACKED_IDS.contains(id))
                    deactivate(id);
                continue;
            }

            SubmarineWaterCullBuffer.updateOcclusionRaw(id, occluders);
            TRACKED_IDS.add(id);
        }

        Iterator<UUID> it = TRACKED_IDS.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!seenIds.contains(id)) {
                SubmarineWaterCullBuffer.updateOcclusionRaw(id, null);
                CACHED_OCCLUDERS.remove(id);
                LAST_SCAN_TICK.remove(id);
                it.remove();
            }
        }
    }

    private static void deactivate(UUID id) {
        SubmarineWaterCullBuffer.updateOcclusionRaw(id, null);
        CACHED_OCCLUDERS.remove(id);
        LAST_SCAN_TICK.remove(id);
        TRACKED_IDS.remove(id);
    }

    private static boolean isBoat(UUID id) {
        return !CompartmentTracker.isSubmarine(id);
    }

    private static boolean isFloating(SubLevel sub) {
        Level oceanLevel;
        BoundingBox3dc bb;
        try {
            oceanLevel = sub.getLevel();
            bb = sub.boundingBox();
        } catch (Throwable t) {
            return false;
        }
        if (oceanLevel == null || bb == null)
            return false;

        int minX = (int) Math.floor(bb.minX());
        int maxX = (int) Math.ceil(bb.maxX());
        int minZ = (int) Math.floor(bb.minZ());
        int maxZ = (int) Math.ceil(bb.maxZ());
        int topY = (int) Math.ceil(bb.maxY()) + 1;
        int botY = (int) Math.floor(bb.minY()) - 1;

        boolean waterAbove = false;
        boolean waterBelow = false;
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                if (!waterAbove && isWaterAt(oceanLevel, x, topY, z))
                    waterAbove = true;
                if (!waterBelow && isWaterAt(oceanLevel, x, botY, z))
                    waterBelow = true;
                if (waterAbove && waterBelow)
                    return false;
            }
        }
        return waterBelow && !waterAbove;
    }

    private static boolean isWaterAt(Level level, int x, int y, int z) {
        try {
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null)
                return false;
            return chunk.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Set<BlockPos> collectOccluders(SubLevel sub) {
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return Set.of();
        BoundingBox3ic bb = plot.getBoundingBox();
        if (bb == null)
            return Set.of();

        int minX = bb.minX(), minY = bb.minY(), minZ = bb.minZ();
        int sX = bb.maxX() - minX + 1, sY = bb.maxY() - minY + 1, sZ = bb.maxZ() - minZ + 1;
        if (sX <= 0 || sY <= 0 || sZ <= 0 || (long) sX * sY * sZ > MAX_SCAN_CELLS)
            return Set.of();

        int layer = sY * sZ;
        boolean[] solid = new boolean[sX * sY * sZ];
        boolean[] fullCube = new boolean[sX * sY * sZ];

        ChunkPos lastPos = null;
        LevelChunk lastChunk = null;
        try {
            for (int x = 0; x < sX; x++) {
                for (int z = 0; z < sZ; z++) {
                    ChunkPos cpos = new ChunkPos((minX + x) >> 4, (minZ + z) >> 4);
                    if (lastPos == null || !lastPos.equals(cpos)) {
                        lastPos = cpos;
                        lastChunk = plot.getChunk(plot.toLocal(cpos));
                    }
                    if (lastChunk == null)
                        continue;
                    for (int y = 0; y < sY; y++) {
                        BlockState state = lastChunk.getBlockState(new BlockPos(minX + x, minY + y, minZ + z));
                        if (state.isAir())
                            continue;
                        int i = x * layer + y * sZ + z;
                        solid[i] = true;
                        fullCube[i] = state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    }
                }
            }
        } catch (Throwable ignored) {
            return Set.of();
        }

        boolean[] exterior = new boolean[sX * sY * sZ];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < sX; x++) {
            for (int y = 0; y < sY; y++) {
                for (int z = 0; z < sZ; z++) {
                    if (x != 0 && x != sX - 1 && y != 0 && y != sY - 1 && z != 0 && z != sZ - 1)
                        continue;
                    int i = x * layer + y * sZ + z;
                    if (!solid[i] && !exterior[i]) {
                        exterior[i] = true;
                        queue.add(i);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i / layer, rem = i % layer, y = rem / sZ, z = rem % sZ;
            if (x > 0) flood(solid, exterior, queue, i - layer);
            if (x < sX - 1) flood(solid, exterior, queue, i + layer);
            if (y > 0) flood(solid, exterior, queue, i - sZ);
            if (y < sY - 1) flood(solid, exterior, queue, i + sZ);
            if (z > 0) flood(solid, exterior, queue, i - 1);
            if (z < sZ - 1) flood(solid, exterior, queue, i + 1);
        }

        Set<BlockPos> out = new HashSet<>();
        for (int x = 0; x < sX; x++) {
            for (int y = 0; y < sY; y++) {
                for (int z = 0; z < sZ; z++) {
                    int i = x * layer + y * sZ + z;
                    boolean occlude;
                    if (!solid[i])
                        occlude = !exterior[i];
                    else if (fullCube[i])
                        occlude = true;
                    else
                        occlude = isBuried(exterior, x, y, z, sX, sY, sZ, layer);
                    if (occlude)
                        out.add(new BlockPos(minX + x, minY + y, minZ + z));
                }
            }
        }
        return out;
    }

    private static boolean isBuried(boolean[] exterior, int x, int y, int z, int sX, int sY, int sZ, int layer) {
        return !isExterior(exterior, x - 1, y, z, sX, sY, sZ, layer)
                && !isExterior(exterior, x + 1, y, z, sX, sY, sZ, layer)
                && !isExterior(exterior, x, y - 1, z, sX, sY, sZ, layer)
                && !isExterior(exterior, x, y + 1, z, sX, sY, sZ, layer)
                && !isExterior(exterior, x, y, z - 1, sX, sY, sZ, layer)
                && !isExterior(exterior, x, y, z + 1, sX, sY, sZ, layer);
    }

    private static boolean isExterior(boolean[] exterior, int x, int y, int z, int sX, int sY, int sZ, int layer) {
        if (x < 0 || x >= sX || y < 0 || y >= sY || z < 0 || z >= sZ)
            return true;
        return exterior[x * layer + y * sZ + z];
    }

    private static void flood(boolean[] solid, boolean[] exterior, ArrayDeque<Integer> queue, int i) {
        if (!solid[i] && !exterior[i]) {
            exterior[i] = true;
            queue.add(i);
        }
    }

    private static void clearAllState() {
        for (UUID id : TRACKED_IDS) {
            SubmarineWaterCullBuffer.updateOcclusionRaw(id, null);
        }
        TRACKED_IDS.clear();
        CACHED_OCCLUDERS.clear();
        LAST_SCAN_TICK.clear();
    }
}
