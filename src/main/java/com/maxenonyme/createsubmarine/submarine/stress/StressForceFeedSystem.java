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
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
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
    private static final Map<UUID, Long> lastBroadcastUHash = new HashMap<>();
    private static final Map<UUID, Integer> arrowLogTick = new HashMap<>();

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

        for (final ServerSubLevel ssl : sslc.getAllSubLevels()) {
            if (ssl.isRemoved()) continue;
            recordForSubLevel(ssl);
        }
    }

    public static void recordForSubLevel(final ServerSubLevel ssl) {
        final ForceGroup stressGroup = getStressForceGroup();
        final ForceGroup internalGroup = getInternalStressForceGroup();
        final ForceGroup buoyancyGroup = getBuoyancyForceGroup();
        if (stressGroup == null) return;

        final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.INSTANCES.get(ssl.getLevel());
        if (analyzer == null) return;

        final LatticeStressSolver solver = analyzer.getSolver(ssl);
        if (solver == null || solver.blockCount() == 0) return;

        final double freshWaterSurface = SubLevelStressAnalyzer.getWaterSurfaceWorldY(ssl);
        solver.refreshWaterDepths(freshWaterSurface);

        if (!ssl.isTrackingIndividualQueuedForces()) {
            ssl.enableIndividualQueuedForcesTracking(true);
        }
        final QueuedForceGroup queued = ssl.getOrCreateQueuedForceGroup(stressGroup);
        final QueuedForceGroup internalQueued = internalGroup != null
            ? ssl.getOrCreateQueuedForceGroup(internalGroup) : queued;
        if (queued == null) return;

        final double[] stressDist = solver.getStressDistribution();
        final int n = solver.blockCount();
        final int totalArrows = recordFaceForces(solver, queued, stressDist, n)
                              + recordInternalForces(solver, internalQueued, n, stressDist);

        // Record buoyancy force from Java-side computation using solver blocks
        if (buoyancyGroup != null) {
            recordBuoyancyForce(ssl, buoyancyGroup, solver, freshWaterSurface);
        }

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
            final double[] u = solver.getU();
            long hash = 0;
            for (int k = 0; k < u.length; k += Math.max(1, u.length / 64)) {
                hash = 31L * hash + Double.doubleToLongBits(u[k]);
            }
            final Long prev = lastBroadcastUHash.get(subId);
            if (prev == null || prev != hash) {
                lastBroadcastUHash.put(subId, hash);
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
                    LOGGER.error("Failed to broadcast stress center for ssl={}: {}",
                        subId.toString().substring(0, 8), e.toString());
                }
            }
        }
    }

    private static void recordBuoyancyForce(final ServerSubLevel ssl, final ForceGroup buoyancyGroup,
                                             final LatticeStressSolver solver, final double waterSurface) {
        final int n = solver.blockCount();
        final BoundingBox3ic bounds = solver.getBounds();

        double totalForce = 0;
        double wx = 0, wy = 0, wz = 0;

        for (int i = 0; i < n; i++) {
            final BlockPos pos = solver.getPosition(i);
            final int worldY = bounds.minY() + pos.getY();
            if (worldY >= waterSurface) continue;

            // Each full block contributes 10.5 N upward (matching Rust's 10.5 * volume * strength)
            final double f = 10.5;
            totalForce += f;
            final double cx = pos.getX() + 0.5;
            final double cy = pos.getY() + 0.5;
            final double cz = pos.getZ() + 0.5;
            wx += f * cx;
            wy += f * cy;
            wz += f * cz;
        }

        if (totalForce < 1e-6) return;

        final QueuedForceGroup queued = ssl.getOrCreateQueuedForceGroup(buoyancyGroup);
        if (queued == null) return;

        final Vector3d center = new Vector3d(wx / totalForce, wy / totalForce, wz / totalForce);
        final Vector3d force = new Vector3d(0, totalForce, 0);
        queued.recordPointForce(center, force);
        LOGGER.debug("Recorded buoyancy force {} N at center ({}, {}, {}) for ssl={}",
            String.format("%.0f", totalForce),
            String.format("%.1f", center.x), String.format("%.1f", center.y), String.format("%.1f", center.z),
            ssl.getUniqueId().toString().substring(0, 8));
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
                                             final int n, final double[] stressDist) {
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

            final double arrowMag = Math.min(frac * maxForce * 0.2, maxForce * 0.1);
            if (arrowMag < 0.01) continue;

            final double cx = pi.getX() + 0.5;
            final double cy = pi.getY() + 0.5;
            final double cz = pi.getZ() + 0.5;

            final double scale = arrowMag / netMag;
            queued.recordPointForce(new Vector3d(cx, cy, cz),
                new Vector3d(netX * scale, netY * scale, netZ * scale));
            arrows++;
        }
        return arrows;
    }

}
