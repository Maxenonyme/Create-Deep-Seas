package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.*;

/**
 * Standalone pipeline for calibrating crush depth physics.
 * Uses the exact same {@link SolverCore} math as in-game.
 *
 * Run via Gradle:
 *   ./gradlew run -PmainClass=com.maxenonyme.createsubmarine.submarine.stress.CalibrationRunner
 *
 * Or from IDE: right-click → Run 'CalibrationRunner.main()'
 */
public class CalibrationRunner {

    public static void main(String[] args) {
        System.out.println("=== Calibration Pipeline ===");
        System.out.println();

        // ---- Build a simple test structure ----
        // A rectangular box: 5 wide × 5 long × 3 tall, solid fill
        // This mimics a simple submarine hull.
        final int width = 5, length = 5, height = 3;
        final double youngsModulus = 1.0e9;   // 1 GPa — soft structural material
        final double yieldStress = 5.0e6;      // 5 MPa

        final List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    blocks.add(new int[]{x, y, z});
                }
            }
        }
        final int n = blocks.size();
        System.out.println("Structure: " + width + "×" + height + "×" + length +
            " = " + n + " blocks");

        // Build arrays
        final int[] sx = new int[n], sy = new int[n], sz = new int[n];
        final double[] E = new double[n];
        final double[] yield = new double[n];
        final double[] volFrac = new double[n];
        final int[] exposedFaceCount = new int[n];
        final boolean[] isHull = new boolean[n];
        final int[][] neighbors = new int[n][SolverCore.MAX_NEIGHBORS];
        final double[][] springK = new double[n][SolverCore.MAX_NEIGHBORS];

        // Position map for neighbor lookup
        final Map<Long, Integer> posToIdx = new HashMap<>();
        for (int i = 0; i < n; i++) {
            sx[i] = blocks.get(i)[0];
            sy[i] = blocks.get(i)[1];
            sz[i] = blocks.get(i)[2];
            E[i] = youngsModulus;
            yield[i] = yieldStress;
            volFrac[i] = 1.0;
            posToIdx.put(key(sx[i], sy[i], sz[i]), i);
        }

        // Build neighbor graph
        int hullCount = 0;
        for (int i = 0; i < n; i++) {
            int exteriorFaces = 0;
            for (int dir = 0; dir < SolverCore.MAX_NEIGHBORS; dir++) {
                final int nx = sx[i] + SolverCore.DX[dir];
                final int ny = sy[i] + SolverCore.DY[dir];
                final int nz = sz[i] + SolverCore.DZ[dir];
                final Integer j = posToIdx.get(key(nx, ny, nz));
                if (j != null) {
                    neighbors[i][dir] = j;
                    final double kVol = 0.5 * (volFrac[i] + volFrac[j]);
                    final double axialK = 0.5 * (E[i] + E[j]) * kVol;
                    springK[i][dir] = -(axialK * SolverCore.INV_DIST[dir] * SolverCore.INV_DIST[dir]);
                } else {
                    neighbors[i][dir] = -1;
                    springK[i][dir] = 0.0;
                    if (dir < 6) exteriorFaces++;
                }
            }
            if (exteriorFaces > 0) {
                isHull[i] = true;
                hullCount++;
            }
            exposedFaceCount[i] = exteriorFaces;
        }
        System.out.println("Hull blocks: " + hullCount + " / " + n);

        // Compute neighborCount (simplified for standalone)
        final int[] neighborCount = new int[n];
        for (int i = 0; i < n; i++) {
            int cnt = 0;
            for (int dir = 0; dir < SolverCore.MAX_NEIGHBORS; dir++) {
                if (neighbors[i][dir] >= 0) cnt++;
            }
            neighborCount[i] = cnt;
        }

        // Allocate solver state
        final double[] u = new double[3 * n];
        final double[] blockWaterDepths = new double[n];

        // Create SolverCore
        final SolverCore solver = new SolverCore(
            n, sx, sy, sz, E, yield, neighbors, springK, neighborCount,
            exposedFaceCount, isHull, hullCount, volFrac, u, blockWaterDepths
        );

        // ---- Test depths ----
        final int[] testDepths = {10, 20, 50, 100, 150, 200, 300};

        System.out.println();
        System.out.println("Depth (bl) | Max stress (Pa) | Crush depth (bl) | Global ratio");
        System.out.println("-----------+-----------------+------------------+-------------");

        for (int depth : testDepths) {
            // Set uniform water depth for all blocks
            Arrays.fill(blockWaterDepths, depth);

            // Solve
            final long t0 = System.nanoTime();
            solver.solve();
            final long dt = System.nanoTime() - t0;

            // Compute results
            double maxStress = 0;
            int maxIdx = -1;
            for (int i = 0; i < n; i++) {
                final double vm = solver.computeVonMises(i);
                if (vm > maxStress) {
                    maxStress = vm;
                    maxIdx = i;
                }
            }

            final double[] crush = solver.computeCrushDepth();
            final int worstBlock = (int) crush[n];
            final double globalCrush = worstBlock >= 0 ? crush[worstBlock] : Double.POSITIVE_INFINITY;

            final double globalRatio = worstBlock >= 0
                ? (depth > 0 ? depth / crush[worstBlock] : 0)
                : 0;

            System.out.printf("%10d | %13.2e | %16.1f | %11.4f  (worst: %d, time: %.2fms)%n",
                depth, maxStress, globalCrush, globalRatio, worstBlock, dt / 1e6);
        }

        // ---- Detailed analysis at max depth ----
        System.out.println();
        System.out.println("=== Detailed analysis at depth = 200 ===");
        Arrays.fill(blockWaterDepths, 200);
        solver.solve();

        double maxVM = 0, minVM = Double.POSITIVE_INFINITY;
        int maxVI = -1, minVI = -1;
        for (int i = 0; i < n; i++) {
            final double vm = solver.computeVonMises(i);
            if (vm > maxVM) { maxVM = vm; maxVI = i; }
            if (vm < minVM) { minVM = vm; minVI = i; }
        }
        System.out.printf("Max von Mises: block %d (at %d,%d,%d) = %.2e Pa%n",
            maxVI, sx[maxVI], sy[maxVI], sz[maxVI], maxVM);
        System.out.printf("Min von Mises: block %d (at %d,%d,%d) = %.2e Pa%n",
            minVI, sx[minVI], sy[minVI], sz[minVI], minVM);

        final double avgE = Arrays.stream(E).summaryStatistics().getAverage();
        System.out.printf("Average Young's modulus: %.2e Pa%n", avgE);
        System.out.printf("Tikhonov alpha fraction: %.4f, alpha = %.2e%n",
            solver.tikhonovAlphaFraction, solver.tikhonovAlphaFraction * avgE);

        System.out.println();
        System.out.println("=== Panel bending ratios ===");
        final double[] panelRatios = solver.computePanelBendingRatios();
        double maxPanel = 0;
        for (int i = 0; i < n; i++) {
            if (panelRatios[i] > maxPanel) maxPanel = panelRatios[i];
        }
        System.out.printf("Max panel bending ratio: %.4f%n", maxPanel);

        // U norm (total displacement)
        double uNorm = 0;
        for (int k = 0; k < 3 * n; k++) uNorm += u[k] * u[k];
        System.out.printf("||u||_2 = %.4e%n", Math.sqrt(uNorm));

        System.out.println();
        System.out.println("=== Done ===");
    }

    private static long key(int x, int y, int z) {
        return (((long) x) & 0x3FFFFFFL) << 38
             | (((long) y) & 0x3FFFFFFL) << 12
             | (((long) z) & 0xFFFL);
    }
}
