package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.StressCenterPayload;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class StressForceFeedSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("StressForceFeed");
    private static final Map<UUID, Long> lastBroadcastTime = new HashMap<>();
    private static final Map<UUID, Integer> arrowLogTick = new HashMap<>();
    private static final Map<UUID, StressSnapshot> lastStressState = new HashMap<>();

    private static final double STRESS_CHANGE_REL_TOLERANCE = 0.01;
    private static final double CRUSH_CHANGE_REL_TOLERANCE = 0.05;

    private record StressSnapshot(
        double maxRatio,
        double meanRatio,
        double minCrush,
        long time
    ) {}

    

    public static final ResourceLocation STRESS_ID = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "stress");
    public static final ResourceLocation INTERNAL_STRESS_ID = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "internal_stresses");
    public static final ResourceLocation BUOYANCY_ID = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "buoyancy");

    public static ForceGroup getStressForceGroup() {
        return ForceGroups.REGISTRY.get(STRESS_ID);
    }

    public static ForceGroup getInternalStressForceGroup() {
        return ForceGroups.REGISTRY.get(INTERNAL_STRESS_ID);
    }

    public static ForceGroup getBuoyancyForceGroup() {
        return ForceGroups.REGISTRY.get(BUOYANCY_ID);
    }

    public static void register() {
        LOGGER.info("StressForceFeedSystem registered");
        ForceGroup fg = getStressForceGroup();
        if (fg != null) {
            LOGGER.info("Stress force group found: defaultDisplayed={}", fg.defaultDisplayed());
        }
    }

    public static void registerListeners() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, ForgeSablePostPhysicsTickEvent.class,
            StressForceFeedSystem::onPostPhysicsTick);
    }

    public static void onPostPhysicsTick(final ForgeSablePostPhysicsTickEvent event) {
        final var level = event.getPhysicsSystem().getLevel();
        final var container = SubLevelContainer.getContainer(level);
        if (!(container instanceof final ServerSubLevelContainer sslc)) return;

        final double timeStep = event.getTimeStep();

        for (final ServerSubLevel ssl : sslc.getAllSubLevels()) {
            if (ssl.isRemoved()) continue;
            recordForSubLevel(ssl, timeStep);
        }
    }

    public static void recordForSubLevel(final ServerSubLevel ssl, final double timeStep) {
        final ForceGroup stressGroup = getStressForceGroup();
        final ForceGroup internalGroup = getInternalStressForceGroup();
        final ForceGroup buoyancyGroup = getBuoyancyForceGroup();
        if (stressGroup == null) return;

        final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.INSTANCES.get(ssl.getLevel());
        if (analyzer == null) return;

        final LatticeStressSolver solver = analyzer.getSolver(ssl);
        if (solver == null || solver.blockCount() == 0) return;

        final double freshWaterSurface = SubLevelStressAnalyzer.getWaterSurfaceWorldY(ssl);
        solver.refreshWaterDepths(freshWaterSurface, ssl.logicalPose());
        solver.resolve();

        if (!ssl.isTrackingIndividualQueuedForces()) {
            ssl.enableIndividualQueuedForcesTracking(true);
        }
        final QueuedForceGroup queued = ssl.getOrCreateQueuedForceGroup(stressGroup);
        final QueuedForceGroup internalQueued = internalGroup != null
            ? ssl.getOrCreateQueuedForceGroup(internalGroup) : queued;
        if (queued == null) return;

        final double[] stressDist = solver.getStressDistribution();
        final int n = solver.blockCount();

        // Log stress UPDATES (only when the distribution changes significantly)
        {
            double maxRatio = 0, sumRatio = 0;
            int worstRatioIdx = -1;
            for (int i = 0; i < n; i++) {
                if (stressDist[i] > maxRatio) { maxRatio = stressDist[i]; worstRatioIdx = i; }
                sumRatio += stressDist[i];
            }
            final double meanRatio = sumRatio / n;
            final double[] crush = solver.computeCrushDepth();
            final int worstIdx = (int) crush[n];
            final double minCrush = worstIdx >= 0 ? crush[worstIdx] : Double.POSITIVE_INFINITY;

            final UUID subId = ssl.getUniqueId();
            final StressSnapshot prev = lastStressState.get(subId);
            final StressSnapshot cur = new StressSnapshot(maxRatio, meanRatio, minCrush, ssl.getLevel().getGameTime());

            if (prev == null) {
                final String vmYieldInfo = worstRatioIdx >= 0
                    ? String.format(" vm=%.2e yield=%.2e E=%.2e",
                        solver.getVonMises(worstRatioIdx), solver.getYieldStress(worstRatioIdx), solver.getYoungsModulus(worstRatioIdx))
                    : "";
                LOGGER.info("STRESS INIT ssl={}: max={} mean={} crush={} bounds={} blocks={} hull={}{}",
                    subId.toString().substring(0, 8),
                    String.format("%.4f", maxRatio * 100),
                    String.format("%.4f", meanRatio * 100),
                    String.format("%.1f", minCrush),
                    solver.getBounds(), n, solver.hullBlockCount(), vmYieldInfo);
                lastStressState.put(subId, cur);
            } else {
                final double dMaxRel = prev.maxRatio > 0 ? Math.abs(maxRatio - prev.maxRatio) / prev.maxRatio : Math.abs(maxRatio - prev.maxRatio);
                final double dMeanRel = prev.meanRatio > 0 ? Math.abs(meanRatio - prev.meanRatio) / prev.meanRatio : Math.abs(meanRatio - prev.meanRatio);
                final double dCrushRel = Double.isFinite(prev.minCrush) && prev.minCrush > 0
                    ? Math.abs(minCrush - prev.minCrush) / prev.minCrush : Double.NaN;
                if (dMaxRel > STRESS_CHANGE_REL_TOLERANCE || dMeanRel > STRESS_CHANGE_REL_TOLERANCE
                        || (Double.isFinite(dCrushRel) && dCrushRel > CRUSH_CHANGE_REL_TOLERANCE)) {
                    final String vmYieldInfo = worstRatioIdx >= 0
                        ? String.format(" vm=%.2e yield=%.2e", solver.getVonMises(worstRatioIdx), solver.getYieldStress(worstRatioIdx))
                        : "";
                    LOGGER.info("STRESS UPDATE ssl={}: max={} (was {}) mean={} (was {}) crush={} (was {}) bounds={} blocks={} hull={}{}",
                        subId.toString().substring(0, 8),
                        String.format("%.4f%%", maxRatio * 100),
                        String.format("%.4f%%", prev.maxRatio * 100),
                        String.format("%.4f%%", meanRatio * 100),
                        String.format("%.4f%%", prev.meanRatio * 100),
                        String.format("%.1f", minCrush),
                        Double.isFinite(prev.minCrush) ? String.format("%.1f", prev.minCrush) : "inf",
                        solver.getBounds(), n, solver.hullBlockCount(), vmYieldInfo);
                    lastStressState.put(subId, cur);
                }
            }
        }

        final int totalArrows = recordFaceForces(solver, queued, stressDist, n)
                              + recordInternalForces(solver, internalQueued, n, stressDist, timeStep);

        // Record actual lift from Sable's FloatingBlockMaterial system
        if (buoyancyGroup != null) {
            recordBuoyancyForce(ssl, buoyancyGroup, solver, timeStep);
        }

        checkStructuralFailure(ssl, solver, analyzer);

        if (totalArrows > 0) {
            final UUID subId = ssl.getUniqueId();
            final int prevLogTick = arrowLogTick.getOrDefault(subId, 0);
            if (prevLogTick == 0 || prevLogTick != totalArrows) {
                LOGGER.debug("Recorded {} arrow(s) for ssl={}",
                    totalArrows, subId.toString().substring(0, 8));
                arrowLogTick.put(subId, totalArrows);
            }
        }

        {
            final UUID subId = ssl.getUniqueId();
            final long gameTime = ssl.getLevel().getGameTime();
            final Long lastTime = lastBroadcastTime.get(subId);
            // Debounce to at most once per 20 ticks (1 second)
            if (lastTime == null || gameTime - lastTime >= 20) {
                lastBroadcastTime.put(subId, gameTime);
                try {
                    BlockPos stressCenterLocal = solver.getStressCenter();
                    BoundingBox3ic bounds = solver.getBounds();
                    BlockPos worldPos = new BlockPos(
                        bounds.minX() + stressCenterLocal.getX(),
                        bounds.minY() + stressCenterLocal.getY(),
                        bounds.minZ() + stressCenterLocal.getZ()
                    );
                    StressCenterPayload payload = new StressCenterPayload(subId, worldPos);
                    for (ServerPlayer player : ssl.getLevel().players()) {
                        if (player.distanceToSqr(worldPos.getX(), worldPos.getY(), worldPos.getZ()) < 256.0 * 256.0) {
                            PacketDistributor.sendToPlayer(player, payload);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to broadcast stress center for ssl={}", subId.toString().substring(0, 8), e);
                }
            }
        }
    }

    private static void checkStructuralFailure(final ServerSubLevel ssl, final LatticeStressSolver solver,
                                                final SubLevelStressAnalyzer analyzer) {
        final int n = solver.blockCount();
        if (n <= 1) return;

        final Level plotLevel = ssl.getLevel();
        boolean brokeAny = false;
        int brokeCount = 0;

        // Log top stress ratios for debugging
        double maxRatio = 0;
        int maxIdx = -1;
        for (int i = 0; i < n; i++) {
            final double r = solver.getStressRatio(i);
            if (r > maxRatio) { maxRatio = r; maxIdx = i; }
        }
        if (maxRatio > 0.5) {
            final BlockPos pos = solver.getPosition(maxIdx);
            final BlockState state = plotLevel.getBlockState(pos);
            LOGGER.debug("checkStructuralFailure ssl={}: n={} maxRatio={} at {} (block={})",
                ssl.getUniqueId().toString().substring(0, 8), n,
                String.format("%.4f", maxRatio), pos.toShortString(),
                state.getBlock().getName().getString());
        }

        for (int i = 0; i < n; i++) {
            final double ratio = solver.getStressRatio(i);
            if (ratio < 1.0) continue;

            final BlockPos pos = solver.getPosition(i);
            final BlockState state = plotLevel.getBlockState(pos);
            if (state.isAir()) continue;

            final double waterDepth = solver.getBlockWaterDepth(i);
            final String matName = state.getBlock().getName().getString();
            final double yield = solver.getYieldStress(i);

            LOGGER.warn("BLOCK FAILURE at {}: material={} stressRatio={} depth={} yield={} E={}",
                pos.toShortString(), matName, String.format("%.4f", ratio),
                String.format("%.1f", waterDepth), String.format("%.2e", yield),
                String.format("%.2e", solver.getYoungsModulus(i)));

            plotLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            brokeAny = true;
            brokeCount++;
        }

        if (brokeAny) {
            final BoundingBox3ic b = solver.getBounds();
            LOGGER.warn("STRUCTURAL FAILURE in {}: {} block(s) broke, bounds=[{},{},{}]-[{},{},{}] blocks={} hull={}",
                ssl.getUniqueId().toString().substring(0, 8), brokeCount,
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(),
                solver.blockCount(), solver.hullBlockCount());
            analyzer.markDirty(ssl);
        }
    }

    private static void recordBuoyancyForce(final ServerSubLevel ssl, final ForceGroup group, final LatticeStressSolver solver, final double timeStep) {
        final double SABLE_BUOYANCY_CONST = 10.5;

        double totalBuoyancy = 0;
        double sumX = 0, sumY = 0, sumZ = 0;
        final int n = solver.blockCount();
        final int hullCount = solver.hullBlockCount();
        int counted = 0;

        for (int i = 0; i < n; i++) {
            final double waterDepth = solver.getBlockWaterDepth(i);
            if (waterDepth <= 0) continue;

            if (!solver.isHullBlock(i)) continue;

            final double submergedFrac = Math.min(Math.max(waterDepth, 0), 1);

            final BlockPos pos = solver.getPosition(i);
            final BlockState state = ssl.getLevel().getBlockState(pos);
            final double blockVolume = PhysicsBlockPropertyHelper.getVolume(state);

            final double b = SABLE_BUOYANCY_CONST * blockVolume * submergedFrac;
            if (b <= 0) continue;

            totalBuoyancy += b;
            sumX += b * (pos.getX() + 0.5);
            sumY += b * (pos.getY() + 0.5);
            sumZ += b * (pos.getZ() + 0.5);
            counted++;
        }

        if (totalBuoyancy <= 0) {
            LOGGER.debug("buoyancy: n={} hull={} total=0 (no underwater hull blocks)", n, hullCount);
            return;
        }

        LOGGER.trace("buoyancy: n={} hull={} counted={} total={}",
            n, hullCount, counted, String.format("%.1f", totalBuoyancy));

        // Multiply by timeStep (impulse convention) so ForceTrackingDispatcher's division recovers correct force
        final double recordedBuoyancy = totalBuoyancy * timeStep;

        final Vector3d localUp = new Vector3d(0, recordedBuoyancy, 0);
        ssl.logicalPose().orientation().transformInverse(localUp);

        final QueuedForceGroup queued = ssl.getOrCreateQueuedForceGroup(group);
        queued.recordPointForce(
            new Vector3d(sumX / totalBuoyancy, sumY / totalBuoyancy, sumZ / totalBuoyancy),
            localUp
        );
    }

    private static int recordFaceForces(final LatticeStressSolver solver, final QueuedForceGroup queued,
                                         final double[] stressDist, final int n) {
        final double[][] faceOff = { {0,0.5,0.5}, {1,0.5,0.5}, {0.5,0,0.5}, {0.5,1,0.5}, {0.5,0.5,0}, {0.5,0.5,1} };
        final double[][] dirVec  = { {-1,0,0}, {1,0,0}, {0,-1,0}, {0,1,0}, {0,0,-1}, {0,0,1} };

        final Map<BlockPos, double[]> faceForce = new HashMap<>();
        for (int i = 0; i < n; i++) {
            final double frac = stressDist[i];
            if (frac <= 0) continue;

            final BlockPos pos = solver.getPosition(i);
            final double[] ff = new double[6];
            int any = 0;
            for (int d = 0; d < 6; d++) {
                if (solver.isFaceExposed(i, d)) {
                    ff[d] = frac * com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.STRESS_FORCE_MAX.get();
                    any++;
                }
            }
            if (any > 0) faceForce.put(pos, ff);
        }

        final int[][][] planeNeighbours = {
            {{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}},
            {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}},
            {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}},
        };

        int totalArrows = 0;

        for (int dir = 0; dir < 6; dir++) {
            final Set<BlockPos> candidates = new HashSet<>();
            for (final Map.Entry<BlockPos, double[]> e : faceForce.entrySet()) {
                if (e.getValue()[dir] > 0) candidates.add(e.getKey());
            }
            if (candidates.isEmpty()) continue;

            final Set<BlockPos> visited = new HashSet<>();
            final int plane = dir / 2;
            final int[][] neighbourDeltas = planeNeighbours[plane];

            for (final BlockPos start : candidates) {
                if (visited.contains(start)) continue;

                final Deque<BlockPos> stack = new ArrayDeque<>();
                stack.push(start);
                double compForce = 0;
                double compWx = 0, compWy = 0, compWz = 0;
                int compCount = 0;

                while (!stack.isEmpty()) {
                    final BlockPos cur = stack.pop();
                    if (!visited.add(cur)) continue;
                    if (!candidates.contains(cur)) continue;

                    final double mag = faceForce.get(cur)[dir];
                    final double[] fo = faceOff[dir];
                    final double cx = cur.getX() + fo[0];
                    final double cy = cur.getY() + fo[1];
                    final double cz = cur.getZ() + fo[2];

                    compForce += mag;
                    compWx += mag * cx;
                    compWy += mag * cy;
                    compWz += mag * cz;
                    compCount++;

                    for (final int[] nd : neighbourDeltas) {
                        stack.push(new BlockPos(
                            cur.getX() + nd[0],
                            cur.getY() + nd[1],
                            cur.getZ() + nd[2]
                        ));
                    }
                }

                if (compForce <= 0) continue;

                final double arrowMag = Math.min(compForce, 1.0e6);
                final Vector3d centroid = new Vector3d(
                    compWx / compForce,
                    compWy / compForce,
                    compWz / compForce
                );
                final double[] dv = dirVec[dir];
                queued.recordPointForce(centroid,
                    new Vector3d(dv[0] * arrowMag, dv[1] * arrowMag, dv[2] * arrowMag));
                totalArrows++;
            }
        }
        return totalArrows;
    }

    private static int recordInternalForces(final LatticeStressSolver solver, final QueuedForceGroup queued,
                                             final int n, final double[] stressDist, final double timeStep) {
        final double[] u = solver.getU();
        final int[] dx = LatticeStressSolver.getDX();
        final int[] dy = LatticeStressSolver.getDY();
        final int[] dz = LatticeStressSolver.getDZ();
        final double[] invDist = LatticeStressSolver.getInvDist();
        final int maxDir = LatticeStressSolver.getMaxNeighbors();

        final double maxForce = com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.STRESS_FORCE_MAX.get();
        int arrows = 0;

        for (int i = 0; i < n; i++) {
            final double frac = stressDist[i];
            if (frac <= 0) continue;

            final BlockPos pi = solver.getPosition(i);
            final int i3 = 3 * i;
            final double uix = u[i3], uiy = u[i3 + 1], uiz = u[i3 + 2];

            double netX = 0, netY = 0, netZ = 0;

            for (int dir = 0; dir < maxDir; dir++) {
                final int j = solver.getNeighbor(i, dir);
                if (j < 0) continue;

                final double Kij = solver.getSpringK(i, dir);
                if (Math.abs(Kij) < 1e-15) continue;

                final int j3 = 3 * j;
                final double dux = u[j3] - uix;
                final double duy = u[j3 + 1] - uiy;
                final double duz = u[j3 + 2] - uiz;

                if (dir < 6) {
                    final int comp = dir / 2;
                    final double sign = (dir % 2 == 0) ? 1.0 : -1.0;
                    final double du = sign * (u[j3 + comp] - (comp == 0 ? uix : comp == 1 ? uiy : uiz));
                    final double fComp = Kij * du * sign;
                    if (comp == 0) netX += fComp;
                    else if (comp == 1) netY += fComp;
                    else netZ += fComp;
                } else {
                    final double cosX = dx[dir] * invDist[dir];
                    final double cosY = dy[dir] * invDist[dir];
                    final double cosZ = dz[dir] * invDist[dir];
                    final double duProj = cosX * dux + cosY * duy + cosZ * duz;
                    final double force = Kij * duProj;
                    netX += force * cosX;
                    netY += force * cosY;
                    netZ += force * cosZ;
                }
            }

            final double netMag = Math.sqrt(netX * netX + netY * netY + netZ * netZ);
            if (netMag < 1e-6) continue;

            // Uniform scale preserves Newton's 3rd law cancellation; 1e6 N → ~8000 displayed
            final double UNIFORM_SCALE = 0.008;
            final double recordedMag = netMag * UNIFORM_SCALE * timeStep;
            if (recordedMag < 0.01) continue;

            final double cx = pi.getX() + 0.5;
            final double cy = pi.getY() + 0.5;
            final double cz = pi.getZ() + 0.5;

            final double scale = recordedMag / netMag;
            queued.recordPointForce(new Vector3d(cx, cy, cz),
                new Vector3d(netX * scale, netY * scale, netZ * scale));
            arrows++;
        }
        return arrows;
    }

}
