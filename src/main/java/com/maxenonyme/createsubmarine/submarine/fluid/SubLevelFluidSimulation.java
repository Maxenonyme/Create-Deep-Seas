package com.maxenonyme.createsubmarine.submarine.fluid;

import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SubLevelFluidSimulation {

    private SubLevelFluidSimulation() {}

    private static final int FULL = SubLevelFluidStore.FULL;
    private static final int LATERAL_MIN_DIFF = 2;

    private static final Map<UUID, LongOpenHashSet> RENDERED = new ConcurrentHashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        if (SubLevelFluidManager.all().isEmpty()) return;

        SubLevelLookup.forEach(event.getServer(), sub -> {
            UUID id = sub.getUniqueId();
            SubLevelFluidStore store = SubLevelFluidManager.get(id);
            if (store == null || store.isEmpty()) return;

            LevelAccessor level = SubLevelLookup.embeddedLevel(sub);
            if (level == null) return;

            Direction gravity = FluidGravity.ofSubLevel(sub);
            step(level, store, gravity);
            project(id, level, store);
        });
    }

    public static void purge(SubLevel sub) {
        UUID id = sub.getUniqueId();
        LongOpenHashSet rendered = RENDERED.remove(id);
        LevelAccessor level = SubLevelLookup.embeddedLevel(sub);
        if (rendered != null && level != null) {
            LongIterator it = rendered.iterator();
            while (it.hasNext()) {
                BlockPos pos = BlockPos.of(it.nextLong());
                if (level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        SubLevelFluidManager.remove(id);
    }

    private static void step(LevelAccessor level, SubLevelFluidStore store, Direction gravity) {
        Direction[] lateral = FluidGravity.lateral(gravity);

        LongArrayList cells = store.snapshotCells();
        sortAgainstGravity(cells, gravity);

        for (int idx = 0; idx < cells.size(); idx++) {
            long packed = cells.getLong(idx);
            int amt = store.get(packed);
            if (amt <= 0) continue;

            BlockPos pos = BlockPos.of(packed);
            BlockPos below = pos.relative(gravity);
            if (!canContain(level, below)) continue;

            long belowKey = below.asLong();
            int space = FULL - store.get(belowKey);
            if (space <= 0) continue;

            int move = Math.min(space, amt);
            store.add(belowKey, move);
            store.add(packed, -move);
        }

        LongArrayList settled = store.snapshotCells();
        for (int idx = 0; idx < settled.size(); idx++) {
            long packed = settled.getLong(idx);
            int amt = store.get(packed);
            if (amt <= 0) continue;

            BlockPos pos = BlockPos.of(packed);
            BlockPos below = pos.relative(gravity);
            if (canContain(level, below) && store.get(below.asLong()) < FULL) {
                continue;
            }

            for (Direction dir : lateral) {
                BlockPos n = pos.relative(dir);
                if (!canContain(level, n)) continue;
                long nKey = n.asLong();
                int diff = amt - store.get(nKey);
                if (diff >= LATERAL_MIN_DIFF) {
                    int move = diff / 2;
                    store.add(nKey, move);
                    amt = store.add(packed, -move);
                }
            }
        }
    }

    private static void project(UUID id, LevelAccessor level, SubLevelFluidStore store) {
        LongOpenHashSet previouslyRendered = RENDERED.computeIfAbsent(id, k -> new LongOpenHashSet());
        LongOpenHashSet nowRendered = new LongOpenHashSet();

        LongArrayList cells = store.snapshotCells();
        for (int idx = 0; idx < cells.size(); idx++) {
            long packed = cells.getLong(idx);
            BlockPos pos = BlockPos.of(packed);
            BlockState target = waterStateForAmount(store.get(packed));
            if (target == null) continue;

            BlockState current = level.getBlockState(pos);
            if (current.getBlock() instanceof LiquidBlock || current.isAir()) {
                if (!current.equals(target)) {
                    level.setBlock(pos, target, 2);
                }
                nowRendered.add(packed);
            }
        }

        LongIterator it = previouslyRendered.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            if (nowRendered.contains(packed)) continue;
            BlockPos pos = BlockPos.of(packed);
            if (level.getBlockState(pos).getBlock() instanceof LiquidBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }

        RENDERED.put(id, nowRendered);
    }

    private static BlockState waterStateForAmount(int amount) {
        if (amount <= 0) return null;
        int waterLevel = Math.max(1, Math.min(8, Math.round(amount * 8f / FULL)));
        if (waterLevel >= 8) {
            return Blocks.WATER.defaultBlockState();
        }
        FlowingFluid water = (FlowingFluid) Fluids.WATER;
        return water.getFlowing(waterLevel, false).createLegacyBlock();
    }

    private static boolean canContain(LevelAccessor level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.getBlock() instanceof LiquidBlock;
    }

    private static void sortAgainstGravity(LongArrayList cells, Direction gravity) {
        final int sign = switch (gravity) {
            case DOWN, WEST, NORTH -> 1;
            case UP, EAST, SOUTH -> -1;
        };
        final Direction.Axis axis = gravity.getAxis();
        java.util.List<Long> boxed = new java.util.ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) boxed.add(cells.getLong(i));
        boxed.sort((a, b) -> Integer.compare(coord(b, axis) * sign, coord(a, axis) * sign));
        cells.clear();
        for (long v : boxed) cells.add(v);
    }

    private static int coord(long packed, Direction.Axis axis) {
        BlockPos p = BlockPos.of(packed);
        return switch (axis) {
            case X -> p.getX();
            case Y -> p.getY();
            case Z -> p.getZ();
        };
    }
}
