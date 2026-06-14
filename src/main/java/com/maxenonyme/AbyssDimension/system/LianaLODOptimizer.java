package com.maxenonyme.AbyssDimension.system;

import com.maxenonyme.AbyssDimension.LianaRegistry;
import com.maxenonyme.AbyssDimension.block.entity.SubmarineLianaBlockEntity;
import com.maxenonyme.createsubmarine.submarine.util.SablePhysicsHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3dc;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

public final class LianaLODOptimizer {

    public static final TagKey<Block> LIANA_LOD_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create_submarine", "liana_lod")
    );

    private static int tickCounter = 0;
    private static final Set<UUID> awakeSubLevels = new HashSet<>();
    private static final Map<UUID, Boolean> isLianaOrPlantCache = new HashMap<>();

    private static final class WakeupCandidate {
        final ServerSubLevel sub;
        final Object handle;
        final double minDistanceSqr;

        WakeupCandidate(ServerSubLevel sub, Object handle, double minDistanceSqr) {
            this.sub = sub;
            this.handle = handle;
            this.minDistanceSqr = minDistanceSqr;
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % 10 != 0) return;
        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) return;

        Set<UUID> presentSubLevels = new HashSet<>();
        List<WakeupCandidate> candidates = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;

            List<ServerPlayer> players = level.players();
            for (SubLevel sub : container.getAllSubLevels()) {
                if (!(sub instanceof ServerSubLevel serverSub)) continue;

                UUID id = serverSub.getUniqueId();
                presentSubLevels.add(id);

                Boolean cachedVal = isLianaOrPlantCache.get(id);
                if (cachedVal == null) {
                    Boolean val = isLianaOrPlantSubLevel(serverSub);
                    if (val != null) {
                        cachedVal = val;
                        isLianaOrPlantCache.put(id, cachedVal);
                        if (cachedVal) {
                            Object h = SablePhysicsHelper.getHandle(serverSub);
                            SablePhysicsHelper.setAsleep(h, true);
                        }
                    } else {
                        continue;
                    }
                }
                if (!cachedVal) continue;

                Vector3dc subPos = serverSub.logicalPose().position();
                double minPlayerDistSqr = Double.MAX_VALUE;

                for (ServerPlayer player : players) {
                    double distSqr = player.distanceToSqr(subPos.x(), subPos.y(), subPos.z());
                    if (distSqr < minPlayerDistSqr) {
                        minPlayerDistSqr = distSqr;
                    }
                }

                Object handle = SablePhysicsHelper.getHandle(serverSub);
                if (handle == null) continue;

                if (minPlayerDistSqr > 225.0) {
                    if (awakeSubLevels.remove(id)) {
                        SablePhysicsHelper.setAsleep(handle, true);
                    }
                } else {
                    if (!awakeSubLevels.contains(id)) {
                        candidates.add(new WakeupCandidate(serverSub, handle, minPlayerDistSqr));
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            for (WakeupCandidate candidate : candidates) {
                SablePhysicsHelper.setAsleep(candidate.handle, false);
                awakeSubLevels.add(candidate.sub.getUniqueId());
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (SubLevel sub : container.getAllSubLevels()) {
                if (!(sub instanceof ServerSubLevel serverSub)) continue;
                if (!isLianaOrPlantCache.getOrDefault(serverSub.getUniqueId(), false)) continue;
                Vector3dc pos = serverSub.logicalPose().position();
                BlockPos worldPos = BlockPos.containing(pos.x(), pos.y(), pos.z());
                int blockLight = level.getBrightness(LightLayer.BLOCK, worldPos);
                BlockPos anchor = serverSub.getPlot().getCenterBlock();
                if (anchor == null) continue;
                ChunkPos anchorLocal = serverSub.getPlot().toLocal(new ChunkPos(anchor));
                LevelChunk anchorChunk = serverSub.getPlot().getChunk(anchorLocal);
                BlockEntity rawBe = anchorChunk == null ? null : anchorChunk.getBlockEntity(anchor);
                if (rawBe instanceof SubmarineLianaBlockEntity be) {
                    be.setWorldLightLevel(blockLight);
                }
            }
        }

        isLianaOrPlantCache.keySet().retainAll(presentSubLevels);
        awakeSubLevels.retainAll(presentSubLevels);
    }

    private static Boolean isLianaOrPlantSubLevel(SubLevel sub) {
        if (sub.getPlot() == null) return null;
        BlockPos anchor = sub.getPlot().getCenterBlock();
        if (anchor == null) return null;
        ChunkPos local = sub.getPlot().toLocal(new ChunkPos(anchor));
        LevelChunk chunk = sub.getPlot().getChunk(local);
        if (chunk == null) return null;
        BlockState state = chunk.getBlockState(anchor);
        return state.is(LIANA_LOD_TAG)
                || state.is(LianaRegistry.LIANA_BLOCK.get())
                || state.is(LianaRegistry.CREEPVINE_SEED.get());
    }
}
