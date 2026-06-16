package com.maxenonyme.highseas;

import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BoatCompartmentDetector {

    private static final int MAX_BLOCKS = 500_000;

    private static final Block HULL_CONTROLLER = BuiltInRegistries.BLOCK
            .get(ResourceLocation.fromNamespaceAndPath("create_submarine", "creative_oxygenator"));

    public record Component(Set<BlockPos> internal, Set<BlockPos> hull, boolean sealed, BlockPos anchor) {
    }

    public record Result(List<Component> components, Set<BlockPos> solidBlocks, boolean hasHullController) {
    }

    private static final class State {
        final LevelPlot plot;
        final int minX, maxX, minY, maxY, minZ, maxZ;
        int curX, curY, curZ;
        final Set<BlockPos> visited = new HashSet<>();
        final Set<BlockPos> solidBlocks = new HashSet<>();
        final List<Component> partial = new ArrayList<>();
        int total = 0;
        boolean hasHullController = false;
        final ChunkCache cache = new ChunkCache();
        Set<BlockPos> activeInternal;
        Set<BlockPos> activeHull;
        boolean activeSealed;
        BlockPos activeAnchor;
        Deque<BlockPos> activeQueue;
        boolean done = false;

        State(LevelPlot plot, BoundingBox3ic b) {
            this.plot = plot;
            this.minX = b.minX();
            this.maxX = b.maxX();
            this.minY = b.minY();
            this.maxY = b.maxY();
            this.minZ = b.minZ();
            this.maxZ = b.maxZ();
            this.curX = minX;
            this.curY = minY;
            this.curZ = minZ;
        }

        boolean hasActiveBfs() {
            return activeQueue != null;
        }

        void startBfs(BlockPos start) {
            activeInternal = new HashSet<>();
            activeHull = new HashSet<>();
            activeSealed = true;
            activeAnchor = start;
            activeQueue = new ArrayDeque<>();
            activeQueue.add(start);
            activeInternal.add(start);
        }

        void finishBfs() {
            partial.add(new Component(
                    Collections.unmodifiableSet(activeInternal),
                    Collections.unmodifiableSet(activeHull),
                    activeSealed,
                    activeAnchor));
            activeInternal = null;
            activeHull = null;
            activeQueue = null;
            activeAnchor = null;
        }

        boolean advanceCursor() {
            curZ++;
            if (curZ > maxZ) {
                curZ = minZ;
                curY++;
            }
            if (curY > maxY) {
                curY = minY;
                curX++;
            }
            return curX <= maxX;
        }
    }

    public static Result detect(SubLevelAccess subAccess) {
        if (!(subAccess instanceof SubLevel sub))
            return new Result(List.of(), Set.of(), false);
        LevelPlot plot = sub.getPlot();
        if (plot == null)
            return new Result(List.of(), Set.of(), false);
        State st = new State(plot, plot.getBoundingBox());
        while (!step(st)) {
        }
        return new Result(Collections.unmodifiableList(st.partial),
                Collections.unmodifiableSet(st.solidBlocks), st.hasHullController);
    }

    private static boolean step(State st) {
        if (st.done)
            return true;

        for (int spent = 0; spent < MAX_BLOCKS; spent++) {
            if (st.total >= MAX_BLOCKS) {
                if (st.hasActiveBfs()) st.finishBfs();
                st.done = true;
                return true;
            }

            if (st.hasActiveBfs()) {
                if (!stepActiveBfs(st)) st.finishBfs();
                continue;
            }

            BlockPos start = new BlockPos(st.curX, st.curY, st.curZ);
            if (st.visited.add(start)) {
                st.total++;
                BlockState startState = getStateInPlot(st.plot, start, st.cache);
                if (startState != null) {
                    if (isPermeable(startState)) {
                        st.startBfs(start);
                    } else {
                        addSolid(st, start, startState);
                    }
                }
            }

            if (!st.advanceCursor()) {
                if (st.hasActiveBfs()) st.finishBfs();
                st.done = true;
                return true;
            }
        }
        return false;
    }

    private static boolean stepActiveBfs(State st) {
        if (st.activeQueue == null || st.activeQueue.isEmpty())
            return false;
        BlockPos curr = st.activeQueue.poll();
        if (curr == null)
            return false;
        for (Direction dir : Direction.values()) {
            BlockPos next = curr.relative(dir);
            if (st.activeInternal.contains(next))
                continue;
            int nx = next.getX(), ny = next.getY(), nz = next.getZ();
            boolean leakSide = nx < st.minX || nx > st.maxX || nz < st.minZ || nz > st.maxZ;
            boolean leakBottom = ny < st.minY;
            if (leakSide || leakBottom) {
                st.activeSealed = false;
                continue;
            }
            if (ny > st.maxY) {
                continue;
            }
            BlockState nextState = getStateInPlot(st.plot, next, st.cache);
            if (nextState == null)
                continue;
            if (isPermeable(nextState)) {
                if (st.visited.contains(next))
                    continue;
                st.visited.add(next);
                st.total++;
                st.activeInternal.add(next);
                st.activeQueue.add(next);
                if (compareLex(next, st.activeAnchor) < 0)
                    st.activeAnchor = next;
                if (st.total >= MAX_BLOCKS)
                    return true;
            } else {
                st.activeHull.add(next);
                if (st.visited.add(next)) {
                    st.total++;
                    addSolid(st, next, nextState);
                }
            }
        }
        return true;
    }

    private static void addSolid(State st, BlockPos pos, BlockState state) {
        st.solidBlocks.add(pos);
        if (state.getBlock() == HULL_CONTROLLER)
            st.hasHullController = true;
    }

    private static class ChunkCache {
        ChunkPos lastPos = null;
        LevelChunk lastChunk = null;
    }

    private static boolean isPermeable(BlockState state) {
        if (state.isAir())
            return true;
        return state.getCollisionShape(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                net.minecraft.core.BlockPos.ZERO).isEmpty();
    }

    private static BlockState getStateInPlot(LevelPlot plot, BlockPos pos, ChunkCache cache) {
        ChunkPos posChunk = new ChunkPos(pos);
        if (cache.lastPos == null || !cache.lastPos.equals(posChunk)) {
            cache.lastPos = posChunk;
            cache.lastChunk = plot.getChunk(plot.toLocal(posChunk));
        }
        if (cache.lastChunk == null)
            return null;
        return cache.lastChunk.getBlockState(pos);
    }

    private static int compareLex(BlockPos a, BlockPos b) {
        int dx = Integer.compare(a.getX(), b.getX());
        if (dx != 0)
            return dx;
        int dy = Integer.compare(a.getY(), b.getY());
        if (dy != 0)
            return dy;
        return Integer.compare(a.getZ(), b.getZ());
    }
}
