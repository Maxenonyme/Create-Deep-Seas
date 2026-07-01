package com.maxenonyme.createsubmarine.submarine.stress;

import java.util.Arrays;

/**
 * Pure-math spring-lattice FEA solver with no Minecraft or Sable dependencies.
 * Used by {@link LatticeStressSolver} for in-game solves, and by
 * {@link CalibrationRunner} for standalone calibration.
 */
public class SolverCore {

    public static final int MAX_NEIGHBORS = 26;
    public static final int[] DX = {
            1, -1, 0, 0, 0, 0,
            1, 1, -1, -1, 0, 0, 0, 0, 1, 1, -1, -1,
            1, 1, 1, 1, -1, -1, -1, -1
    };
    public static final int[] DY = {
            0, 0, 1, -1, 0, 0,
            1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0,
            1, 1, -1, -1, 1, 1, -1, -1
    };
    public static final int[] DZ = {
            0, 0, 0, 0, 1, -1,
            0, 0, 0, 0, 1, 1, -1, -1, 1, -1, 1, -1,
            1, -1, 1, -1, 1, -1, 1, -1
    };

    public static final double[] INV_DIST = new double[MAX_NEIGHBORS];
    public static final double[][] DIR_COS = new double[MAX_NEIGHBORS][3];

    public static final double RHO_G = 10000.0;
    public static final double MOON_POOL_FACTOR = 0.8;
    public static final double PLATE_BENDING_BETA = 0.3;

    static {
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final double d = Math.sqrt(DX[dir]*DX[dir] + DY[dir]*DY[dir] + DZ[dir]*DZ[dir]);
            INV_DIST[dir] = 1.0 / d;
            DIR_COS[dir][0] = DX[dir] / d;
            DIR_COS[dir][1] = DY[dir] / d;
            DIR_COS[dir][2] = DZ[dir] / d;
        }
    }

    // ---- graph data (immutable after construction) ----
    public final int n;
    public final int[] x, y, z;
    public final double[] E;
    public final double[] yieldStress;
    public final int[][] neighbors;
    public final double[][] springK;
    public final int[] neighborCount;
    public final int[] exposedFaceCount;
    public final boolean[] isHullBlock;
    public final int hullBlockCount;
    public final double[] volFraction;

    // ---- mutable solver state ----
    public final double[] u;
    public final double[] blockWaterDepths;

    // ---- config parameters ----
    public double poissonRatio = 0.3;
    public double tikhonovAlphaFraction = 0.01;
    public double rhoG = RHO_G;
    public boolean useMoonPool = true;

    public long solveTimeNanos = 0;

    public SolverCore(
        final int n,
        final int[] x,
        final int[] y,
        final int[] z,
        final double[] E,
        final double[] yieldStress,
        final int[][] neighbors,
        final double[][] springK,
        final int[] neighborCount,
        final int[] exposedFaceCount,
        final boolean[] isHullBlock,
        final int hullBlockCount,
        final double[] volFraction,
        final double[] u,
        final double[] blockWaterDepths
    ) {
        this.n = n;
        this.x = x;
        this.y = y;
        this.z = z;
        this.E = E;
        this.yieldStress = yieldStress;
        this.neighbors = neighbors;
        this.springK = springK;
        this.neighborCount = neighborCount;
        this.exposedFaceCount = exposedFaceCount;
        this.isHullBlock = isHullBlock;
        this.hullBlockCount = hullBlockCount;
        this.volFraction = volFraction;
        this.u = u;
        this.blockWaterDepths = blockWaterDepths;
    }

    // ============================================================
    //  Build RHS — hydrostatic pressure on exposed faces
    // ============================================================

    public void buildRHS(final double[] b) {
        Arrays.fill(b, 0.0);
        boolean anyUnderwater = false;
        for (int i = 0; i < n; i++) {
            final double depth = blockWaterDepths[i];
            if (depth <= 0) continue;
            anyUnderwater = true;
            for (int dir = 0; dir < 6; dir++) {
                if (neighbors[i][dir] >= 0) continue;
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? -1.0 : 1.0;
                double localPressure = this.rhoG * depth * volFraction[i];
                if (useMoonPool && dir == 0) {
                    // downward(-Y) exposed face: check if there's a hull block below
                    final int belowIdx = findBlock(x[i] + DX[dir], y[i] + DY[dir], z[i] + DZ[dir]);
                    if (belowIdx >= 0 && isHullBlock[belowIdx]) {
                        localPressure *= MOON_POOL_FACTOR;
                    }
                }
                b[3 * i + comp] += -sign * localPressure;
            }
        }
        if (!anyUnderwater) {
            Arrays.fill(b, 0.0);
        }
    }

    // ============================================================
    //  Sparse mat-vec: Ku = K * uvec
    // ============================================================

    public void applyK(final double[] uvec, final double[] Ku) {
        Arrays.fill(Ku, 0.0);
        for (int i = 0; i < n; i++) {
            final int i3 = 3 * i;
            final double uix = uvec[i3], uiy = uvec[i3 + 1], uiz = uvec[i3 + 2];
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                final int j = neighbors[i][dir];
                if (j < 0) continue;
                final double Kij = springK[i][dir];
                final int j3 = 3 * j;
                if (dir < 6) {
                    final int comp = dir / 2;
                    final double sign = (dir % 2 == 0) ? 1.0 : -1.0;
                    final double du = sign * (uvec[j3 + comp] - (comp == 0 ? uix : comp == 1 ? uiy : uiz));
                    Ku[i3 + comp] += Kij * du * sign;
                } else {
                    final double cosX = DIR_COS[dir][0];
                    final double cosY = DIR_COS[dir][1];
                    final double cosZ = DIR_COS[dir][2];
                    final double duProj = cosX * (uvec[j3] - uix) + cosY * (uvec[j3 + 1] - uiy) + cosZ * (uvec[j3 + 2] - uiz);
                    final double force = Kij * duProj;
                    Ku[i3] += force * cosX;
                    Ku[i3 + 1] += force * cosY;
                    Ku[i3 + 2] += force * cosZ;
                }
            }
        }
    }

    // ============================================================
    //  Conjugate Gradient solver with Tikhonov regularization
    // ============================================================

    public void solveCG(final double[] b) {
        solveCG(b, 0);
    }

    public void solveCG(final double[] b, final int maxIterOverride) {
        final int N3 = 3 * n;
        double bNorm = 0;
        for (int k = 0; k < N3; k++) bNorm += b[k] * b[k];
        if (bNorm < 1e-30) {
            Arrays.fill(u, 0.0);
            return;
        }

        final double avgE = n > 0 ? Arrays.stream(E).summaryStatistics().getAverage() : 0.0;
        final double tikhonovAlpha = tikhonovAlphaFraction * avgE;

        final double[] r = new double[N3];
        final double[] p = new double[N3];
        final double[] Ap = new double[N3];

        applyK(u, r);
        double rr = 0;
        for (int k = 0; k < N3; k++) {
            r[k] = b[k] - r[k] - tikhonovAlpha * u[k];
            rr += r[k] * r[k];
        }
        System.arraycopy(r, 0, p, 0, N3);
        double rrOld = rr;

        final int maxIter = maxIterOverride > 0 ? maxIterOverride : Math.max(300, N3);
        for (int iter = 0; iter < maxIter; iter++) {
            applyK(p, Ap);
            for (int k = 0; k < N3; k++) Ap[k] += tikhonovAlpha * p[k];
            final double pAp = dot(p, Ap);
            if (pAp <= 0) break;

            final double alpha = rr / pAp;
            for (int k = 0; k < N3; k++) {
                u[k] += alpha * p[k];
                r[k] -= alpha * Ap[k];
            }

            rr = dot(r, r);
            if (rr < 1e-12 * bNorm) break;

            final double beta = rr / rrOld;
            for (int k = 0; k < N3; k++) p[k] = r[k] + beta * p[k];
            rrOld = rr;
        }
    }

    // ============================================================
    //  Rigid-body mode removal (6 modes: 3 translation + 3 rotation)
    // ============================================================

    public void removeRigidBodyMode(final double[] v) {
        double sumX = 0, sumY = 0, sumZ = 0;
        for (int i = 0; i < n; i++) {
            sumX += v[3 * i];
            sumY += v[3 * i + 1];
            sumZ += v[3 * i + 2];
        }
        final double avgX = sumX / n;
        final double avgY = sumY / n;
        final double avgZ = sumZ / n;

        double cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < n; i++) {
            cx += x[i]; cy += y[i]; cz += z[i];
        }
        cx /= n; cy /= n; cz /= n;

        double Lx = 0, Ly = 0, Lz = 0;
        double Ixx = 0, Iyy = 0, Izz = 0;
        for (int i = 0; i < n; i++) {
            double rx = x[i] - cx;
            double ry = y[i] - cy;
            double rz = z[i] - cz;
            Lx += ry * v[3*i+2] - rz * v[3*i+1];
            Ly += rz * v[3*i] - rx * v[3*i+2];
            Lz += rx * v[3*i+1] - ry * v[3*i];
            Ixx += ry*ry + rz*rz;
            Iyy += rx*rx + rz*rz;
            Izz += rx*rx + ry*ry;
        }
        double wx = Ixx > 1e-30 ? Lx / Ixx : 0;
        double wy = Iyy > 1e-30 ? Ly / Iyy : 0;
        double wz = Izz > 1e-30 ? Lz / Izz : 0;

        for (int i = 0; i < n; i++) {
            final double rx = x[i] - cx;
            final double ry = y[i] - cy;
            final double rz = z[i] - cz;
            v[3*i]   -= avgX + (wy * rz - wz * ry);
            v[3*i+1] -= avgY + (wz * rx - wx * rz);
            v[3*i+2] -= avgZ + (wx * ry - wy * rx);
        }
    }

    // ============================================================
    //  Von Mises stress
    // ============================================================

    public double computeVonMises(final int blockIdx) {
        final double Ei = E[blockIdx];
        final int i3 = 3 * blockIdx;
        final double uix = u[i3], uiy = u[i3 + 1], uiz = u[i3 + 2];

        double gxx = 0, gxy = 0, gxz = 0;
        double gyx = 0, gyy = 0, gyz = 0;
        double gzx = 0, gzy = 0, gzz = 0;
        double denomX = 0, denomY = 0, denomZ = 0;

        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = neighbors[blockIdx][dir];
            if (j < 0) continue;
            final int j3 = 3 * j;
            final double dux = u[j3] - uix;
            final double duy = u[j3 + 1] - uiy;
            final double duz = u[j3 + 2] - uiz;
            final double dx = DX[dir], dy = DY[dir], dz = DZ[dir];

            gxx += dux * dx; gxy += dux * dy; gxz += dux * dz;
            gyx += duy * dx; gyy += duy * dy; gyz += duy * dz;
            gzx += duz * dx; gzy += duz * dy; gzz += duz * dz;
            denomX += dx * dx; denomY += dy * dy; denomZ += dz * dz;
        }

        if (denomX > 0) { final double inv = 1.0 / denomX; gxx *= inv; gyx *= inv; gzx *= inv; }
        if (denomY > 0) { final double inv = 1.0 / denomY; gxy *= inv; gyy *= inv; gzy *= inv; }
        if (denomZ > 0) { final double inv = 1.0 / denomZ; gxz *= inv; gyz *= inv; gzz *= inv; }

        final double exx = gxx;
        final double eyy = gyy;
        final double ezz = gzz;
        final double exy = (gxy + gyx) * 0.5;
        final double eyz = (gyz + gzy) * 0.5;
        final double ezx = (gxz + gzx) * 0.5;

        final double nu = poissonRatio;
        final double lambda = Ei * nu / ((1.0 + nu) * (1.0 - 2.0 * nu));
        final double mu = Ei / (2.0 * (1.0 + nu));
        final double eVol = exx + eyy + ezz;
        final double sxx = 2.0 * mu * exx + lambda * eVol;
        final double syy = 2.0 * mu * eyy + lambda * eVol;
        final double szz = 2.0 * mu * ezz + lambda * eVol;
        final double sxy = 2.0 * mu * exy;
        final double syz = 2.0 * mu * eyz;
        final double szx = 2.0 * mu * ezx;

        return Math.sqrt(0.5 * (
                (sxx - syy) * (sxx - syy) +
                (syy - szz) * (syy - szz) +
                (szz - sxx) * (szz - sxx) +
                6.0 * (sxy * sxy + syz * syz + szx * szx)
        ));
    }

    // ============================================================
    //  Crush depth
    // ============================================================

    public double[] computeCrushDepth() {
        final double[] result = new double[n + 1];
        double globalDepth = Double.POSITIVE_INFINITY;
        int worstBlock = -1;

        for (int i = 0; i < n; i++) {
            if (yieldStress[i] <= 0) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double blockDepth = blockWaterDepths[i];
            if (blockDepth <= 0) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double vm = computeVonMises(i);
            if (vm <= 1e-30) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double depth = blockDepth * yieldStress[i] / vm;
            if (depth > 1e15) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            result[i] = depth;
            if (depth < globalDepth) {
                globalDepth = depth;
                worstBlock = i;
            }
        }
        result[n] = (double) worstBlock;
        return result;
    }

    // ============================================================
    //  Panel bending analysis
    // ============================================================

    public double[] computePanelBendingRatios() {
        final double[] ratios = new double[n];

        for (int dir = 0; dir < 6; dir++) {
            final int[] planeDirs = getPlaneDirections(dir);
            if (planeDirs == null) continue;

            final java.util.Set<Integer> candidates = new java.util.HashSet<>();
            for (int i = 0; i < n; i++) {
                if (neighbors[i][dir] >= 0) continue;
                if (blockWaterDepths[i] <= 0) continue;
                if (yieldStress[i] <= 0) continue;
                candidates.add(i);
            }
            if (candidates.isEmpty()) continue;

            final java.util.Set<Integer> visited = new java.util.HashSet<>();
            for (int start : candidates) {
                if (visited.contains(start)) continue;

                final java.util.Set<Integer> patch = new java.util.HashSet<>();
                final java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
                stack.push(start);
                while (!stack.isEmpty()) {
                    int cur = stack.pop();
                    if (!visited.add(cur)) continue;
                    if (!candidates.contains(cur)) continue;
                    patch.add(cur);
                    for (int pd : planeDirs) {
                        int ni = neighbors[cur][pd];
                        if (ni >= 0) stack.push(ni);
                    }
                }
                if (patch.size() < 2) continue;

                int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
                int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
                for (int idx : patch) {
                    int uu, vv;
                    switch (dir / 2) {
                        case 0: uu = y[idx]; vv = z[idx]; break;
                        case 1: uu = x[idx]; vv = z[idx]; break;
                        default: uu = x[idx]; vv = y[idx]; break;
                    }
                    if (uu < minU) minU = uu;
                    if (uu > maxU) maxU = uu;
                    if (vv < minV) minV = vv;
                    if (vv > maxV) maxV = vv;
                }
                final double shortSpan = Math.min(maxU - minU + 1, maxV - minV + 1);
                if (shortSpan < 2) continue;

                double avgDepth = 0;
                for (int idx : patch) avgDepth += blockWaterDepths[idx];
                avgDepth /= patch.size();
                final double P = this.rhoG * avgDepth;
                final double bendingStress = PLATE_BENDING_BETA * P * shortSpan * shortSpan;

                for (int idx : patch) {
                    ratios[idx] = bendingStress / yieldStress[idx];
                }
            }
        }
        return ratios;
    }

    private static int[] getPlaneDirections(final int faceDir) {
        switch (faceDir) {
            case 0: case 1: return new int[]{2, 3, 4, 5};
            case 2: case 3: return new int[]{0, 1, 4, 5};
            case 4: case 5: return new int[]{0, 1, 2, 3};
            default: return null;
        }
    }

    public double[] computeCombinedStressRatios() {
        final double[] vmRatios = getStressDistribution();
        final double[] panelRatios = computePanelBendingRatios();
        final double[] combined = new double[n];
        for (int i = 0; i < n; i++) {
            combined[i] = Math.max(vmRatios[i], panelRatios[i]);
        }
        return combined;
    }

    public double[] getStressDistribution() {
        final double[] dist = new double[n];
        for (int i = 0; i < n; i++) {
            if (blockWaterDepths[i] <= 0) {
                dist[i] = 0;
            } else {
                final double vm = computeVonMises(i);
                if (vm <= 1e-30) {
                    dist[i] = 0;
                } else {
                    dist[i] = Math.min(1.0, vm / yieldStress[i]);
                }
            }
        }
        return dist;
    }

    // ============================================================
    //  Utilities
    // ============================================================

    public static double dot(final double[] a, final double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    public int findBlock(final int bx, final int by, final int bz) {
        for (int i = 0; i < n; i++) {
            if (x[i] == bx && y[i] == by && z[i] == bz) return i;
        }
        return -1;
    }

    public void solve() {
        final long t0 = System.nanoTime();
        final double[] b = new double[3 * n];
        buildRHS(b);
        solveCG(b);
        removeRigidBodyMode(u);
        solveTimeNanos = System.nanoTime() - t0;
    }
}
