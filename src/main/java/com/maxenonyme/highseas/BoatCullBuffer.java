package com.maxenonyme.highseas;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.ryanhcode.sable.util.BoundedBitVolume3i;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BoatCullBuffer {

    private static final Map<UUID, WaterOcclusionRegion> REGIONS = new HashMap<>();

    private BoatCullBuffer() {
    }

    public static void push(UUID id, Set<BlockPos> union) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        mc.execute(() -> {
            WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
            if (container == null)
                return;
            WaterOcclusionRegion old = REGIONS.remove(id);
            if (old != null)
                container.removeRegion(old);
            if (union == null || union.isEmpty())
                return;

            List<BlockPos> filtered = filterToCubes(mc.level, id, union);
            if (filtered.isEmpty())
                return;

            BoundedBitVolume3i volume = BoundedBitVolume3i.fromBlocks(filtered);
            if (volume == null)
                return;
            WaterOcclusionRegion region = container.addRegion(volume);
            if (region != null)
                REGIONS.put(id, region);
        });
    }

    private static List<BlockPos> filterToCubes(Level level, UUID id, Set<BlockPos> union) {
        SubLevelContainer subContainer = SubLevelContainer.getContainer(level);
        SubLevel sub = subContainer == null ? null : subContainer.getSubLevel(id);
        LevelPlot plot = sub == null ? null : sub.getPlot();
        if (plot == null)
            return new ArrayList<>(union);

        List<BlockPos> out = new ArrayList<>(union.size());
        ChunkPos lastCp = null;
        LevelChunk lastChunk = null;
        for (BlockPos pos : union) {
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
            boolean fillsCell = state.isAir()
                    || state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (fillsCell || isBuried(union, pos))
                out.add(pos);
        }
        return out;
    }

    private static boolean isBuried(Set<BlockPos> union, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (!union.contains(pos.relative(dir)))
                return false;
        }
        return true;
    }
}
