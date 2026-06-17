package com.maxenonyme.highseas;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

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
    private static final double MAX_TILT = 0.6;

    private static final Set<UUID> TRACKED_IDS = new HashSet<>();
    private static final Map<UUID, Set<BlockPos>> CACHED_UNION = new HashMap<>();
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

            if (!isUprightAndFloating(sub)) {
                if (TRACKED_IDS.contains(id))
                    deactivate(id);
                continue;
            }

            Long lastScan = LAST_SCAN_TICK.get(id);
            Set<BlockPos> union = CACHED_UNION.get(id);
            boolean needRescan = lastScan == null || union == null || (now - lastScan) >= UPDATE_INTERVAL_TICKS;

            if (needRescan) {
                union = scan(sub);
                CACHED_UNION.put(id, union);
                LAST_SCAN_TICK.put(id, now);
            } else if (TRACKED_IDS.contains(id)) {
                continue;
            }

            if (union.isEmpty()) {
                if (TRACKED_IDS.contains(id))
                    deactivate(id);
                continue;
            }

            BoatCullBuffer.push(id, union);
            TRACKED_IDS.add(id);
        }

        Iterator<UUID> it = TRACKED_IDS.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!seenIds.contains(id)) {
                BoatCullBuffer.push(id, null);
                CACHED_UNION.remove(id);
                LAST_SCAN_TICK.remove(id);
                it.remove();
            }
        }
    }

    private static Set<BlockPos> scan(SubLevel sub) {
        BoatCompartmentDetector.Result result = BoatCompartmentDetector.detect(sub);
        if (result.hasHullController())
            return Set.of();
        return computeUnion(result);
    }

    private static Set<BlockPos> computeUnion(BoatCompartmentDetector.Result result) {
        Set<BlockPos> union = new HashSet<>();
        boolean anySealed = false;
        for (BoatCompartmentDetector.Component c : result.components()) {
            if (!c.sealed())
                continue;
            anySealed = true;
            union.addAll(c.internal());
            union.addAll(c.hull());
        }
        if (anySealed)
            union.addAll(result.solidBlocks());
        return union;
    }

    private static void deactivate(UUID id) {
        BoatCullBuffer.push(id, null);
        CACHED_UNION.remove(id);
        LAST_SCAN_TICK.remove(id);
        TRACKED_IDS.remove(id);
    }

    private static boolean isUprightAndFloating(SubLevel sub) {
        Level oceanLevel;
        BoundingBox3dc bb;
        try {
            oceanLevel = sub.getLevel();
            bb = sub.boundingBox();
            Vector3d up = new Vector3d(0, 1, 0);
            sub.logicalPose().orientation().transform(up);
            double tilt = Math.acos(Math.max(-1.0, Math.min(1.0, up.y())));
            if (tilt > MAX_TILT)
                return false;
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

    private static void clearAllState() {
        for (UUID id : TRACKED_IDS) {
            BoatCullBuffer.push(id, null);
        }
        TRACKED_IDS.clear();
        CACHED_UNION.clear();
        LAST_SCAN_TICK.clear();
    }
}
