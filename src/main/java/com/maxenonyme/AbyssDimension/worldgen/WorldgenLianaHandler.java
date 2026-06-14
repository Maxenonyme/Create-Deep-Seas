package com.maxenonyme.AbyssDimension.worldgen;

import com.maxenonyme.AbyssDimension.LianaRegistry;
import com.maxenonyme.AbyssDimension.block.entity.SubmarineLianaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;

public final class WorldgenLianaHandler {
    private static final Set<BlockPos> pendingInit = new HashSet<>();

    private WorldgenLianaHandler() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        Level level = chunk.getLevel();
        if (level == null || level.isClientSide) return;

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int maxX = chunk.getPos().getMaxBlockX();
        int maxZ = chunk.getPos().getMaxBlockZ();

        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
            BlockEntity be = chunk.getBlockEntity(pos);
            if (be instanceof SubmarineLianaBlockEntity liana) {
                if (liana.getBlockState().is(LianaRegistry.LIANA_BLOCK.get())) {
                    pendingInit.add(pos.immutable());
                }
            }
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk)) return;
        pendingInit.removeIf(pos -> {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            return cx == event.getChunk().getPos().x && cz == event.getChunk().getPos().z;
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (pendingInit.isEmpty()) return;
        java.util.Iterator<BlockPos> it = pendingInit.iterator();
        int processed = 0;
        while (it.hasNext() && processed < 64) {
            BlockPos pos = it.next();
            it.remove();
            Level level = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
                    .getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (level == null) continue;
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SubmarineLianaBlockEntity liana) {
                if (liana.getBlockState().is(LianaRegistry.LIANA_BLOCK.get())) {
                    liana.setChanged();
                }
            }
            processed++;
        }
    }
}
