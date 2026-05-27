package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentDetector;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload;
import com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SubmarinePressureSystem {
    private static final int TICK_INTERVAL = 20;
    private static final int MAX_WATER_SCAN = 400;
    private static final ResourceLocation SOUND_METAL_STRESS = ResourceLocation.withDefaultNamespace("entity.iron_golem.repair");
    private static int tickCounter = 0;
    private static final Random RAND = new Random();

    private static final Map<UUID, Set<BlockPos>> BREACHED_PLOT = new ConcurrentHashMap<>();
    private static final Map<UUID, List<CompartmentDetector.Component>> KNOWN_SEALED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CACHED_WATER_DEPTH = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<BlockPos, Integer>> CRACK_LEVELS = new ConcurrentHashMap<>();

    public static void setSealedCompartments(UUID id, List<CompartmentDetector.Component> comps) {
        KNOWN_SEALED.put(id, comps);
    }

    public static void clearSubmarine(UUID id) {
        BREACHED_PLOT.remove(id);
        KNOWN_SEALED.remove(id);
        CACHED_WATER_DEPTH.remove(id);
        CRACK_LEVELS.remove(id);
    }

    public static void clearAll() {
        BREACHED_PLOT.clear();
        KNOWN_SEALED.clear();
        CACHED_WATER_DEPTH.clear();
        CRACK_LEVELS.clear();
    }

    public static Map<UUID, Map<BlockPos, Integer>> getAllCracks() {
        return CRACK_LEVELS;
    }

    public static boolean hasCrack(UUID id, BlockPos plotPos) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        return cracks != null && cracks.containsKey(plotPos);
    }

    public static boolean repairCrack(UUID id, BlockPos plotPos, SubLevelAccess sub, Level oceanLevel) {
        Map<BlockPos, Integer> cracks = CRACK_LEVELS.get(id);
        if (cracks == null || !cracks.containsKey(plotPos)) return false;
        cracks.remove(plotPos);
        sendCrackPacket(oceanLevel, id, plotPos, -1, 0);
        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldVec);
        BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);
        oceanLevel.playSound(null, worldPos, net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.BLOCKS, 0.6f, 1.0f + RAND.nextFloat() * 0.3f);
        return true;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TICK_INTERVAL != 0) return;
        if (com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.DISABLE_IMPLOSION.get()) return;

        for (Map.Entry<UUID, SubLevelAccess> entry : SubLevelRegistry.getAll().entrySet()) {
            processSubmarine(entry.getKey(), entry.getValue());
        }
    }

    private static void processSubmarine(UUID id, SubLevelAccess sub) {
        Level plotLevel = SubLevelRegistry.getLevel(id);
        if (plotLevel == null) return;

        SubLevelRegistry.PlotBounds bounds = SubLevelRegistry.getBounds(id);
        if (bounds == null) return;

        Level oceanLevel = sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl ? sl.getLevel() : plotLevel;

        Vector3dc subCenter = sub.logicalPose().position();
        int waterDepth = measureWaterDepth(oceanLevel, id, subCenter);
        CACHED_WATER_DEPTH.put(id, waterDepth);

        if (waterDepth <= 0) {
            BREACHED_PLOT.remove(id);
            return;
        }

        Set<BlockPos> breached = BREACHED_PLOT.get(id);
        if (breached != null && !breached.isEmpty() && !hasAnySealedCompartment(id)) {
            BREACHED_PLOT.remove(id);
            SubmarineSinkingSystem.onCrashed(id, sub, plotLevel, bounds);
            return;
        }

        boolean[] creakPlayed = { false };
        long volume = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1) * (bounds.maxZ() - bounds.minZ() + 1);
        int samples = (int) Math.min(250, Math.max(15, volume / 150));

        for (int i = 0; i < samples; i++) {
            BlockPos plotPos = bounds.randomInside(RAND);
            if (plotPos == null || (breached != null && breached.contains(plotPos))) continue;

            BlockState state = plotLevel.getBlockState(plotPos);
            if (state.isAir() || state.getFluidState().isSource()) continue;

            Optional<HullStrengthConfig.HullProperty> propOpt = HullStrengthConfig.getFor(state);
            if (propOpt.isEmpty()) continue;
            HullStrengthConfig.HullProperty prop = propOpt.get();

            if (waterDepth <= prop.maxWaterDepth()) continue;

            applyPressure(id, plotLevel, oceanLevel, sub, plotPos, state, prop, creakPlayed);
        }
    }

    private static int measureWaterDepth(Level level, UUID id, Vector3dc subCenter) {
        int x = (int) Math.round(subCenter.x());
        int z = (int) Math.round(subCenter.z());

        net.minecraft.world.phys.AABB aabb = CompartmentTracker.getWorldAABB(id);
        int startY = aabb != null
                ? (int) Math.ceil(aabb.maxY) + 1
                : (int) Math.round(subCenter.y());

        try {
            if (!level.getFluidState(new BlockPos(x, startY, z)).is(net.minecraft.tags.FluidTags.WATER)) {
                return 0;
            }
        } catch (Exception ignored) {
            return 0;
        }

        int highestWaterY = startY;
        try {
            for (int y = startY; y < Math.min(startY + MAX_WATER_SCAN, level.getMaxBuildHeight()); y++) {
                BlockState state = level.getBlockState(new BlockPos(x, y, z));
                if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                    highestWaterY = y;
                } else if (state.isAir() && y >= level.getSeaLevel()) {
                    break;
                }
            }
        } catch (Exception ignored) {}

        int measuredDepth = highestWaterY - startY + 1;
        int seaLevelDepth = level.getSeaLevel() - startY;

        return Math.max(measuredDepth, seaLevelDepth);
    }

    private static void applyPressure(UUID id, Level plotLevel, Level oceanLevel, SubLevelAccess sub, BlockPos plotPos, BlockState state, HullStrengthConfig.HullProperty prop, boolean[] creakPlayed) {
        CompartmentDetector.Component comp = CompartmentTracker.findCompartmentAdjacent(id, plotPos);
        if (comp == null) return;
        if (CompartmentTracker.isCompromised(id, comp.anchor())) return;

        if (!comp.hull().contains(plotPos)) return;

        boolean facesExterior = false;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = plotPos.relative(dir);
            if (!comp.hull().contains(neighbor) && !comp.internal().contains(neighbor)) {
                facesExterior = true;
                break;
            }
        }
        if (!facesExterior) return;

        if (RAND.nextFloat() >= prop.implosionChance()) return;

        Vector3d worldVec = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        sub.logicalPose().transformPosition(worldVec);
        BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);

        if (!creakPlayed[0]) {
            SoundEvent creak = BuiltInRegistries.SOUND_EVENT.get(SOUND_METAL_STRESS);
            if (creak != null) {
                float pitch = 0.15f + RAND.nextFloat() * 0.15f;
                oceanLevel.playSound(null, worldPos, creak, SoundSource.BLOCKS, 0.45f, pitch);
                creakPlayed[0] = true;
            }
        }

        Map<BlockPos, Integer> cracks = CRACK_LEVELS.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        int crackLevel = cracks.getOrDefault(plotPos, 0) + 1;
        int blockId = BuiltInRegistries.BLOCK.getId(state.getBlock());

        if (crackLevel >= 3) {
            cracks.remove(plotPos);
            sendCrackPacket(oceanLevel, id, plotPos, -1, 0);

            SoundType soundType = SoundType.STONE;
            try { soundType = state.getBlock().getSoundType(state, plotLevel, plotPos, null); } catch (Exception ignored) {}
            oceanLevel.playSound(null, worldPos, soundType.getBreakSound(), SoundSource.BLOCKS, 1.6f, 0.65f + RAND.nextFloat() * 0.3f);
            if (oceanLevel instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SPLASH, worldVec.x, worldVec.y + 0.5, worldVec.z, 25, 0.4, 0.1, 0.4, 0.2);
            }

            plotLevel.destroyBlock(plotPos, false);
            BREACHED_PLOT.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(plotPos);
        } else {
            cracks.put(plotPos, crackLevel);
            sendCrackPacket(oceanLevel, id, plotPos, crackLevel, blockId);
            if (oceanLevel instanceof ServerLevel serverLevel) {
                int count = crackLevel == 1 ? 5 : 12;
                serverLevel.sendParticles(ParticleTypes.DRIPPING_WATER, worldVec.x, worldVec.y, worldVec.z, count, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private static void sendCrackPacket(Level oceanLevel, UUID id, BlockPos plotPos, int crackLevel, int blockId) {
        if (!(oceanLevel instanceof ServerLevel sl)) return;
        SubCrackPayload payload = new SubCrackPayload(id, plotPos, crackLevel, blockId);
        for (net.minecraft.server.level.ServerPlayer player : sl.players()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static boolean hasAnySealedCompartment(UUID id) {
        List<CompartmentDetector.Component> comps = KNOWN_SEALED.get(id);
        if (comps == null || comps.isEmpty()) return true;

        for (CompartmentDetector.Component c : comps) {
            if (c.sealed() && !CompartmentTracker.isCompromised(id, c.anchor())) return true;
        }
        return false;
    }
}
