package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.joml.Vector3d;

import java.util.*;

public class ShapeClassifier {

    public record HullBlock(BlockPos pos, double[] rawNormals, double weight) {}
    public record ClassificationResult(
        Map<BlockPos, Vector3d> smoothedNormals,
        double coherence,
        double minCurvatureRadius,
        int hullBlockCount,
        double rawExposedFaceAvg,
        Map<BlockPos, Integer> effectiveFaceCounts
    ) {}

    public static ClassificationResult classify(
        final Map<BlockPos, Set<Integer>> exposedFaces,
        final double kernelRadius
    ) {
        if (exposedFaces.isEmpty()) {
            return new ClassificationResult(Map.of(), 1.0, Double.MAX_VALUE, 0, 0, Map.of());
        }

        double kr = kernelRadius > 0 ? kernelRadius : (SubmarineConfig.KERNEL_RADIUS != null ? SubmarineConfig.KERNEL_RADIUS.get() : 1.5);
        double krSq = kr * kr;

        Map<BlockPos, Vector3d> rawNormals = new HashMap<>();
        Map<BlockPos, Double> rawFaceCounts = new HashMap<>();
        for (var entry : exposedFaces.entrySet()) {
            BlockPos pos = entry.getKey();
            Set<Integer> faces = entry.getValue();
            double nx = 0, ny = 0, nz = 0;
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for (int f : faces) {
                if (f >= 0 && f < 6) {
                    nx += dirs[f][0];
                    ny += dirs[f][1];
                    nz += dirs[f][2];
                }
            }
            double len = Math.sqrt(nx*nx + ny*ny + nz*nz);
            if (len > 1e-10) {
                rawNormals.put(pos, new Vector3d(nx/len, ny/len, nz/len));
                rawFaceCounts.put(pos, (double) faces.size());
            }
        }

        if (rawNormals.isEmpty()) {
            return new ClassificationResult(Map.of(), 1.0, Double.MAX_VALUE, 0, 0, Map.of());
        }

        List<BlockPos> hullPositions = new ArrayList<>(rawNormals.keySet());
        Map<BlockPos, Vector3d> smoothedNormals = new HashMap<>();
        Map<BlockPos, Integer> effectiveFaceCounts = new HashMap<>();

        for (BlockPos pos : hullPositions) {
            Vector3d rn = rawNormals.get(pos);
            double sx = rn.x * 0.6, sy = rn.y * 0.6, sz = rn.z * 0.6;
            double totalW = 0.6;

            for (BlockPos neighbor : hullPositions) {
                if (neighbor.equals(pos)) continue;
                double dx = neighbor.getX() - pos.getX();
                double dy = neighbor.getY() - pos.getY();
                double dz = neighbor.getZ() - pos.getZ();
                double dSq = dx*dx + dy*dy + dz*dz;
                if (dSq > krSq) continue;

                Vector3d nn = rawNormals.get(neighbor);
                double w = 1.0 / (1.0 + Math.sqrt(dSq));
                sx += nn.x * w;
                sy += nn.y * w;
                sz += nn.z * w;
                totalW += w;
            }

            if (totalW > 0) {
                double len = Math.sqrt(sx*sx + sy*sy + sz*sz);
                if (len > 1e-10) {
                    smoothedNormals.put(pos, new Vector3d(sx/len, sy/len, sz/len));
                } else {
                    smoothedNormals.put(pos, new Vector3d(rn));
                }
            } else {
                smoothedNormals.put(pos, new Vector3d(rn));
            }

            Vector3d sn = smoothedNormals.get(pos);
            double effFaces = 0;
            int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
            for (int f : exposedFaces.getOrDefault(pos, Set.of())) {
                if (f >= 0 && f < 6) {
                    double dot = sn.x * dirs[f][0] + sn.y * dirs[f][1] + sn.z * dirs[f][2];
                    if (dot > 0.3) effFaces += dot;
                }
            }
            effectiveFaceCounts.put(pos, Math.max(1, (int) Math.round(effFaces)));
        }

        double totalAngle = 0;
        for (BlockPos pos : hullPositions) {
            Vector3d rn = rawNormals.get(pos);
            Vector3d sn = smoothedNormals.get(pos);
            double dot = rn.x * sn.x + rn.y * sn.y + rn.z * sn.z;
            dot = Math.max(-1.0, Math.min(1.0, dot));
            double angleRad = Math.acos(dot);
            totalAngle += angleRad;
        }
        double avgAngleDeg = Math.toDegrees(totalAngle / hullPositions.size());
        double coherence = 1.0 - avgAngleDeg / 90.0;

        final double gaussianErrorThreshold = SubmarineConfig.GAUSSIAN_MAX_ERROR != null
            ? SubmarineConfig.GAUSSIAN_MAX_ERROR.get() : 0.05;
        if (coherence < 1.0 - gaussianErrorThreshold) {
            effectiveFaceCounts.clear();
            for (BlockPos pos : hullPositions) {
                effectiveFaceCounts.put(pos, Math.max(1, rawFaceCounts.getOrDefault(pos, 1.0).intValue()));
            }
        }

        double maxAngleVar = 0;
        for (BlockPos pos : hullPositions) {
            Vector3d sn = smoothedNormals.get(pos);
            for (BlockPos neighbor : hullPositions) {
                if (neighbor.equals(pos)) continue;
                double dx = neighbor.getX() - pos.getX();
                double dy = neighbor.getY() - pos.getY();
                double dz = neighbor.getZ() - pos.getZ();
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (dist < 0.5 || dist > 3) continue;

                Vector3d nn = smoothedNormals.get(neighbor);
                double dot = sn.x * nn.x + sn.y * nn.y + sn.z * nn.z;
                dot = Math.max(-1.0, Math.min(1.0, dot));
                double angleRad = Math.acos(dot);
                double curvature = angleRad / dist;
                if (curvature > maxAngleVar) maxAngleVar = curvature;
            }
        }
        double minRadius = maxAngleVar > 1e-10 ? (1.0 / maxAngleVar) : Double.MAX_VALUE;

        double avgFaces = rawFaceCounts.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return new ClassificationResult(
            Collections.unmodifiableMap(smoothedNormals),
            coherence,
            minRadius,
            hullPositions.size(),
            avgFaces,
            Collections.unmodifiableMap(effectiveFaceCounts)
        );
    }

    public static boolean useAnalytical(double coherence, double minRadius, int minApproxRadius) {
        if (minRadius < minApproxRadius) return false;
        double thresh = SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL.get() : 0.85;
        return coherence >= thresh;
    }

    public static boolean useCorrected(double coherence) {
        double thresh = SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED.get() : 0.60;
        return coherence >= thresh;
    }

    public static double roughnessPenalty(double coherence) {
        if (useAnalytical(coherence, Double.MAX_VALUE, 0)) return 0;
        if (!useCorrected(coherence)) return 0;
        double aThresh = SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_ANALYTICAL.get() : 0.85;
        double cThresh = SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED != null
            ? SubmarineConfig.COHERENCE_THRESHOLD_CORRECTED.get() : 0.60;
        double penalty = SubmarineConfig.ROUGHNESS_PENALTY != null
            ? SubmarineConfig.ROUGHNESS_PENALTY.get() : 0.05;
        double t = (aThresh - coherence) / (aThresh - cThresh);
        return penalty * Math.max(0, Math.min(1, t));
    }
}
