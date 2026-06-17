package com.maxenonyme.createsubmarine.submarine.stress;

import com.maxenonyme.createsubmarine.submarine.compat.CopycatsCompat;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public class LatticeStressSolver {

    private static final int MAX_NEIGHBORS = 26;
    private static final int[] DX = {
            1, -1, 0, 0, 0, 0,       
            1, 1, -1, -1, 0, 0, 0, 0, 1, 1, -1, -1,   
            1, 1, 1, 1, -1, -1, -1, -1   
    };
    private static final int[] DY = {
            0, 0, 1, -1, 0, 0,
            1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0,
            1, 1, -1, -1, 1, 1, -1, -1
    };
    private static final int[] DZ = {
            0, 0, 0, 0, 1, -1,
            0, 0, 0, 0, 1, 1, -1, -1, 1, -1, 1, -1,
            1, -1, 1, -1, 1, -1, 1, -1
    };

    private static final double[] INV_DIST = new double[MAX_NEIGHBORS];
    private static final double[][] DIR_COS = new double[MAX_NEIGHBORS][3];

    private static final int[] DIR_TO_MC = {5, 4, 1, 0, 3, 2};

    static {
        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final double d = Math.sqrt(DX[dir]*DX[dir] + DY[dir]*DY[dir] + DZ[dir]*DZ[dir]);
            INV_DIST[dir] = 1.0 / d;
            DIR_COS[dir][0] = DX[dir] / d;
            DIR_COS[dir][1] = DY[dir] / d;
            DIR_COS[dir][2] = DZ[dir] / d;
        }
    }

    public static double RHO_G = 10000.0;
    private double rhoG = RHO_G;
    private static final Set<Runnable> CONFIG_LOAD_HOOKS = new java.util.LinkedHashSet<>();

    public static void loadConfigValues() {
        try {
            if (SubmarineConfig.WATER_DENSITY_GRAVITY != null)
                RHO_G = SubmarineConfig.WATER_DENSITY_GRAVITY.get();
        } catch (Exception e) { /* keep default */ }
        for (Runnable h : CONFIG_LOAD_HOOKS) h.run();
        CONFIG_LOAD_HOOKS.clear();
    }

    static {
        // Support both pre- and post-config loading:
        if (SubmarineConfig.WATER_DENSITY_GRAVITY != null) {
            try { RHO_G = SubmarineConfig.WATER_DENSITY_GRAVITY.get(); } catch (Exception ignored) {}
        }
    }

    public void setFluidDensityMultiplier(double multiplier) {
        this.rhoG = RHO_G * multiplier;
    }

    private final int n;
    private final BlockPos[] positions;
    private final double[] E;
    private final double[] yieldStress;
    private final int[][] neighbors;
    private final double[][] springK;

    private final double[] u;
    private final double[] blockWaterDepths;
    private final int[] exposedFaceCount;
    private final boolean[] isHullBlock;
    private int hullBlockCount;
    private final int[] faceBlockCounts;

    private final BoundingBox3ic bounds;
    private Pose3dc subLevelPose;
    private double waterSurfaceWorldY;

    private final double[] volFraction;
    private final Map<BlockPos, Vector3d> smoothedNormals;
    private final Map<BlockPos, Integer> effectiveFaceCounts;
    private final double coherence;
    private final Quaterniondc orientation;
    private final boolean orientationEnabled;
    private static double MOON_POOL_FACTOR = 0.8;
    private static boolean ORIENTATION_ENABLED = true;
    private static boolean COPYCAT_INHERIT = false;

    private long solveTimeNanos = 0;
    private final long structureHash;

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds) {
        this(level, bounds, null, null, null, null, Double.POSITIVE_INFINITY);
    }

    // Package-private constructor for programmatic use — takes pre-built arrays,
    // no BlockGetter needed. solve() is called with null (it doesn't use the param).
    LatticeStressSolver(
        final int n,
        final BlockPos[] positions,
        final double[] E,
        final double[] yieldStress,
        final int[][] neighbors,
        final double[][] springK,
        final double[] u,
        final double[] blockWaterDepths,
        final int[] exposedFaceCount,
        final boolean[] isHullBlock,
        final int hullBlockCount,
        final BoundingBox3ic bounds,
        final long structureHash
    ) {
        this.n = n;
        this.positions = positions;
        this.E = E;
        this.yieldStress = yieldStress;
        this.neighbors = neighbors;
        this.springK = springK;
        this.u = u;
        this.blockWaterDepths = blockWaterDepths;
        this.exposedFaceCount = exposedFaceCount;
        this.isHullBlock = isHullBlock;
        this.hullBlockCount = hullBlockCount;
        this.faceBlockCounts = new int[6];
        this.bounds = bounds;
        this.structureHash = structureHash;
        this.subLevelPose = null;
        this.waterSurfaceWorldY = Double.POSITIVE_INFINITY;
        this.volFraction = new double[n];
        java.util.Arrays.fill(this.volFraction, 1.0);
        this.smoothedNormals = Map.of();
        this.effectiveFaceCounts = Map.of();
        this.coherence = 1.0;
        this.orientation = new org.joml.Quaterniond();
        this.orientationEnabled = false;
        final long t0 = System.nanoTime();
        solve(null);
        this.solveTimeNanos = System.nanoTime() - t0;
    }

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds, final double[] previousU) {
        this(level, bounds, previousU, null, null, null, Double.POSITIVE_INFINITY);
    }

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds,
                                final double[] previousU,
                                final ShapeClassifier.ClassificationResult classification,
                                final Quaterniondc shipOrientation) {
        this(level, bounds, previousU, classification, shipOrientation, null, Double.POSITIVE_INFINITY);
    }

    public LatticeStressSolver(final BlockGetter level, final BoundingBox3ic bounds,
                                final double[] previousU,
                                final ShapeClassifier.ClassificationResult classification,
                                final Quaterniondc shipOrientation,
                                final Pose3dc subLevelPose,
                                final double waterSurfaceWorldY) {
        this.bounds = bounds;
        this.subLevelPose = subLevelPose;
        this.waterSurfaceWorldY = waterSurfaceWorldY;
        this.smoothedNormals = (classification != null) ? classification.smoothedNormals() : Map.of();
        this.effectiveFaceCounts = (classification != null) ? classification.effectiveFaceCounts() : Map.of();
        this.coherence = (classification != null) ? classification.coherence() : 1.0;
        this.orientation = (shipOrientation != null) ? shipOrientation : new org.joml.Quaterniond();
        this.orientationEnabled = ORIENTATION_ENABLED && shipOrientation != null;

        final List<BlockPos> posList = new ArrayList<>();
        final Long2IntOpenHashMap posToIdx = new Long2IntOpenHashMap();
        posToIdx.defaultReturnValue(-1);

        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    mutable.set(x, y, z);
                    final BlockState state = level.getBlockState(mutable);
                    if (includeBlock(state, level, mutable)) {
                        final int idx = posList.size();
                        posList.add(mutable.immutable());
                        posToIdx.put(BlockPos.asLong(x, y, z), idx);
                    }
                }
            }
        }

        this.n = posList.size();
        this.positions = posList.toArray(new BlockPos[0]);

        long hash = 0;
        for (final BlockPos p : this.positions) {
            hash ^= BlockPos.asLong(p.getX(), p.getY(), p.getZ());
        }
        this.structureHash = hash;

        this.E = new double[this.n];
        this.yieldStress = new double[this.n];
        this.volFraction = new double[this.n];
        this.exposedFaceCount = new int[this.n];
        this.isHullBlock = new boolean[this.n];
        this.hullBlockCount = 0;

        for (int i = 0; i < this.n; i++) {
            final BlockPos pos = this.positions[i];
            final BlockState state = level.getBlockState(pos);

            if (COPYCAT_INHERIT && CopycatsCompat.isCopycatBlock(state) && level instanceof net.minecraft.world.level.Level lvl) {
                double[] props = CopycatsCompat.getMaterialProperties(lvl, pos, state);
                this.E[i] = props[0];
                this.yieldStress[i] = props[1];
            } else {
                this.E[i] = DefaultMaterialProperties.getYoungsModulus(state);
                if (this.E[i] <= 0) this.E[i] = 1.0;
                this.yieldStress[i] = DefaultMaterialProperties.getYieldStress(state);
            }

            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                this.volFraction[i] = 1.0;
            } else {
                double vol = shape.bounds().getXsize() * shape.bounds().getYsize() * shape.bounds().getZsize();
                this.volFraction[i] = Math.min(1.0, Math.max(0.01, vol));
            }
        }

        this.neighbors = new int[this.n][MAX_NEIGHBORS];
        this.springK = new double[this.n][MAX_NEIGHBORS];

        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final BlockState blockState = level.getBlockState(p);
            final double Ei = this.E[i];
            int exteriorFaces = 0;
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                final int nx = p.getX() + DX[dir];
                final int ny = p.getY() + DY[dir];
                final int nz = p.getZ() + DZ[dir];
                final long key = BlockPos.asLong(nx, ny, nz);
                final int j = posToIdx.get(key);

                if (j >= 0) {
                    mutable.set(nx, ny, nz);
                    final BlockState neighborState = level.getBlockState(mutable);
                    if (includeBlock(neighborState, level, mutable) && facesTouch(dir, blockState, level, p, neighborState, level, mutable)) {
                        this.neighbors[i][dir] = j;
                        final double kVol = 0.5 * (this.volFraction[i] + this.volFraction[j]);
                        final double factorI = DefaultMaterialProperties.getDirectionalFactor(blockState, DX[dir], DY[dir], DZ[dir]);
                        final double factorJ = DefaultMaterialProperties.getDirectionalFactor(neighborState, -DX[dir], -DY[dir], -DZ[dir]);
                        final double axialK = 0.5 * (Ei * factorI + this.E[j] * factorJ) * kVol;
                        this.springK[i][dir] = -(axialK * INV_DIST[dir] * INV_DIST[dir]);
                    } else {
                        this.neighbors[i][dir] = -1;
                        this.springK[i][dir] = 0.0;
                        if (dir < 6) this.exposedFaceCount[i]++;
                    }
                } else {
                    this.neighbors[i][dir] = -1;
                    this.springK[i][dir] = 0.0;
                    if (dir < 6) this.exposedFaceCount[i]++;
                }

                if (dir < 6) {
                    if (nx < bounds.minX() || nx > bounds.maxX() ||
                            ny < bounds.minY() || ny > bounds.maxY() ||
                            nz < bounds.minZ() || nz > bounds.maxZ()) {
                        exteriorFaces++;
                    }
                }
            }

            if (exposedFaceCount[i] > 0) {
                this.isHullBlock[i] = true;
                this.hullBlockCount++;
            }
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < this.n; i++) {
            if (!this.isHullBlock[i]) continue;
            BlockPos p = this.positions[i];
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        this.faceBlockCounts = new int[]{0, 0, 0, 0, 0, 0};
        for (int i = 0; i < this.n; i++) {
            if (!this.isHullBlock[i]) continue;
            BlockPos p = this.positions[i];
            if (p.getX() == maxX) this.faceBlockCounts[0]++;
            if (p.getX() == minX) this.faceBlockCounts[1]++;
            if (p.getY() == maxY) this.faceBlockCounts[2]++;
            if (p.getY() == minY) this.faceBlockCounts[3]++;
            if (p.getZ() == maxZ) this.faceBlockCounts[4]++;
            if (p.getZ() == minZ) this.faceBlockCounts[5]++;
        }

        this.u = new double[3 * this.n];
        this.blockWaterDepths = new double[this.n];
        if (previousU != null && previousU.length == 3 * this.n) {
            System.arraycopy(previousU, 0, this.u, 0, 3 * this.n);
        }

        final long t0 = System.nanoTime();
        solve(level);
        this.solveTimeNanos = System.nanoTime() - t0;
    }

    private static boolean includeBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() || state.isSolid();
    }

    public static boolean includeBlockForHash(BlockState state, BlockGetter level, BlockPos pos) {
        return includeBlock(state, level, pos);
    }

    /** Safe access to a ModConfigSpec config value — returns def if not loaded. */
    private static double cfgDouble(final Object val, final double def) {
        if (val == null) return def;
        try {
            if (val instanceof net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> cv) {
                final Object v = cv.get();
                return v instanceof Number n ? n.doubleValue() : def;
            }
        } catch (Exception ignored) {}
        return def;
    }

    private static boolean facesTouch(int dir, BlockState aState, BlockGetter aLevel, BlockPos aPos,
                                       BlockState bState, BlockGetter bLevel, BlockPos bPos) {
        if (dir >= 6) return true;
        Direction face = Direction.from3DDataValue(DIR_TO_MC[dir]);
        VoxelShape aShape = aState.getCollisionShape(aLevel, aPos);
        VoxelShape bShape = bState.getCollisionShape(bLevel, bPos);
        if (aShape.isEmpty() || bShape.isEmpty()) return true;
        double aMax, bMin;
        switch (face.getAxis()) {
            case X: aMax = aShape.bounds().maxX; bMin = bShape.bounds().minX; break;
            case Y: aMax = aShape.bounds().maxY; bMin = bShape.bounds().minY; break;
            default: aMax = aShape.bounds().maxZ; bMin = bShape.bounds().minZ; break;
        }
        return aMax >= bMin;
    }

    public static Map<BlockPos, Set<Integer>> buildExposedFaceMap(
            final BlockGetter level, final BoundingBox3ic bounds) {
        final Map<BlockPos, Set<Integer>> result = new HashMap<>();
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        final Long2IntOpenHashMap present = new Long2IntOpenHashMap();
        present.defaultReturnValue(-1);

        int minX = bounds.maxX() + 1, maxX = bounds.minX() - 1;
        int minY = bounds.maxY() + 1, maxY = bounds.minY() - 1;
        int minZ = bounds.maxZ() + 1, maxZ = bounds.minZ() - 1;
        int found = 0;

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    mutable.set(x, y, z);
                    BlockState state = level.getBlockState(mutable);
                    if (includeBlock(state, level, mutable)) {
                        present.put(BlockPos.asLong(x, y, z), 1);
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        if (z < minZ) minZ = z;
                        if (z > maxZ) maxZ = z;
                        found++;
                    }
                }
            }
        }

        if (found == 0) return result;

        final int cropMinX = minX, cropMaxX = maxX;
        final int cropMinY = minY, cropMaxY = maxY;
        final int cropMinZ = minZ, cropMaxZ = maxZ;

        for (long key : present.keySet()) {
            BlockPos pos = BlockPos.of(key);
            BlockState state = level.getBlockState(pos);
            for (int dir = 0; dir < 6; dir++) {
                int nx = pos.getX() + (dir == 0 ? 1 : dir == 1 ? -1 : 0);
                int ny = pos.getY() + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                int nz = pos.getZ() + (dir == 4 ? 1 : dir == 5 ? -1 : 0);
                int neighborIdx = present.get(BlockPos.asLong(nx, ny, nz));
                if (neighborIdx < 0) {
                    result.computeIfAbsent(pos, k -> new HashSet<>()).add(dir);
                } else {
                    BlockState neighborState = level.getBlockState(new BlockPos(nx, ny, nz));
                    if (!facesTouch(dir, state, level, pos, neighborState, level, new BlockPos(nx, ny, nz))) {
                        result.computeIfAbsent(pos, k -> new HashSet<>()).add(dir);
                    }
                }
                if (nx < cropMinX || nx > cropMaxX ||
                    ny < cropMinY || ny > cropMaxY ||
                    nz < cropMinZ || nz > cropMaxZ) {
                    result.computeIfAbsent(pos, k -> new HashSet<>()).add(dir);
                }
            }
        }
        return result;
    }

    private void buildRHS(final double[] b) {
        java.util.Arrays.fill(b, 0.0);
        final Vector3d localDown = new Vector3d(0, -1, 0);
        if (this.orientationEnabled) {
            this.orientation.transform(localDown);
        }

        if (!Double.isFinite(this.waterSurfaceWorldY)) {
            buildRHSLegacy(b, localDown);
            return;
        }

        final Vector3d tmpVec = new Vector3d();
        final Vector3d worldPos = new Vector3d();
        boolean anyUnderwater = false;
        java.util.Arrays.fill(this.blockWaterDepths, 0.0);

        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double worldMinY = computeBlockMinWorldY(p.getX(), p.getY(), p.getZ(), tmpVec, worldPos);
            if (worldMinY >= this.waterSurfaceWorldY) continue;
            final double waterDepth = this.waterSurfaceWorldY - worldMinY;
            this.blockWaterDepths[i] = waterDepth;
            anyUnderwater = true;

            for (int dir = 0; dir < 6; dir++) {
                if (this.neighbors[i][dir] >= 0) continue;
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? -1.0 : 1.0;
                double localPressure = this.rhoG * waterDepth * this.volFraction[i];
                final Direction faceDir = Direction.from3DDataValue(dir);

                if (this.orientationEnabled) {
                    double nx = faceDir.getStepX();
                    double ny = faceDir.getStepY();
                    double nz = faceDir.getStepZ();
                    double dot = nx * localDown.x + ny * localDown.y + nz * localDown.z;
                    if (dot < 0) continue;
                    localPressure *= dot + 0.5;
                }

                double faceDot = faceDir.getStepX() * localDown.x + faceDir.getStepY() * localDown.y + faceDir.getStepZ() * localDown.z;
                if (faceDot > 0.7) {
                    BlockPos below = this.positions[i].relative(faceDir);
                    for (int j = 0; j < this.n; j++) {
                        if (this.positions[j].equals(below) && this.isHullBlock[j]) {
                            localPressure *= MOON_POOL_FACTOR;
                            break;
                        }
                    }
                }

                b[3 * i + comp] += -sign * localPressure;
            }
        }

        if (!anyUnderwater) {
            java.util.Arrays.fill(b, 0.0);
        }
    }

    private void buildRHSLegacy(final double[] b, final Vector3d localDown) {
        double minDepth = Double.POSITIVE_INFINITY, maxDepth = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double d = p.getX() * localDown.x + p.getY() * localDown.y + p.getZ() * localDown.z;
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
        }
        final double height = Math.max(1, maxDepth - minDepth + 1);

        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double depth = (p.getX() * localDown.x + p.getY() * localDown.y + p.getZ() * localDown.z) - minDepth;
            this.blockWaterDepths[i] = depth + 0.5;
            double pressure = this.rhoG * (0.5 + depth / height);

            for (int dir = 0; dir < 6; dir++) {
                if (this.neighbors[i][dir] >= 0) continue;
                final int comp = dir / 2;
                final double sign = (dir % 2 == 0) ? -1.0 : 1.0;
                double localPressure = pressure * this.volFraction[i];
                final Direction faceDir = Direction.from3DDataValue(dir);

                if (this.orientationEnabled) {
                    double nx = faceDir.getStepX();
                    double ny = faceDir.getStepY();
                    double nz = faceDir.getStepZ();
                    double dot = nx * localDown.x + ny * localDown.y + nz * localDown.z;
                    if (dot < 0) continue;
                    localPressure *= dot + 0.5;
                }

                double faceDot = faceDir.getStepX() * localDown.x + faceDir.getStepY() * localDown.y + faceDir.getStepZ() * localDown.z;
                if (faceDot > 0.7) {
                    BlockPos below = this.positions[i].relative(faceDir);
                    for (int j = 0; j < this.n; j++) {
                        if (this.positions[j].equals(below) && this.isHullBlock[j]) {
                            localPressure *= MOON_POOL_FACTOR;
                            break;
                        }
                    }
                }

                b[3 * i + comp] += -sign * localPressure;
            }
        }
    }

    private void solve(final BlockGetter level) {
        final double[] b = new double[3 * this.n];
        buildRHS(b);
        solveCG(b);
    }

    private void solveCG(final double[] b) {
        double bNorm = 0;
        for (int k = 0; k < 3 * this.n; k++) bNorm += b[k] * b[k];
        if (bNorm < 1e-30) {
            java.util.Arrays.fill(this.u, 0.0);
            return;
        }

        final double[] r = new double[3 * this.n];
        final double[] p = new double[3 * this.n];
        final double[] Ap = new double[3 * this.n];

        final double tikhonovAlpha = this.n > 0
            ? cfgDouble(SubmarineConfig.TIKHONOV_ALPHA_FRACTION, 0.01) * E[0]
            : 0.0;


        applyK(this.u, r);
        double rr = 0;
        for (int k = 0; k < 3 * this.n; k++) {
            r[k] = b[k] - r[k] - tikhonovAlpha * this.u[k];
            rr += r[k] * r[k];
        }

        System.arraycopy(r, 0, p, 0, 3 * this.n);
        double rrOld = rr;

        final int maxIter = Math.max(300, 3 * this.n);
        for (int iter = 0; iter < maxIter; iter++) {
            applyK(p, Ap);
            for (int k = 0; k < 3 * this.n; k++) Ap[k] += tikhonovAlpha * p[k];
            final double pAp = dot(p, Ap);
            if (pAp <= 0) break;

            final double alpha = rr / pAp;
            for (int k = 0; k < 3 * this.n; k++) {
                this.u[k] += alpha * p[k];
                r[k] -= alpha * Ap[k];
            }

            removeRigidBodyMode(r);

            rr = dot(r, r);
            if (rr < 1e-24 * bNorm) break;

            final double beta = rr / rrOld;
            for (int k = 0; k < 3 * this.n; k++) p[k] = r[k] + beta * p[k];
            rrOld = rr;
        }
    }

    public void resolve() {
        final long t0 = System.nanoTime();
        final double[] b = new double[3 * this.n];
        buildRHS(b);
        solveCG(b);
        this.solveTimeNanos = System.nanoTime() - t0;
    }

    private void removeRigidBodyMode(final double[] v) {
        double sumX = 0, sumY = 0, sumZ = 0;
        for (int i = 0; i < this.n; i++) {
            sumX += v[3 * i];
            sumY += v[3 * i + 1];
            sumZ += v[3 * i + 2];
        }
        final double avgX = sumX / this.n;
        final double avgY = sumY / this.n;
        final double avgZ = sumZ / this.n;
        for (int i = 0; i < this.n; i++) {
            v[3 * i] -= avgX;
            v[3 * i + 1] -= avgY;
            v[3 * i + 2] -= avgZ;
        }
    }

    private static final int PAR_THRESHOLD = 5000;
    private static final ForkJoinPool FJP = ForkJoinPool.commonPool();

    private void applyK(final double[] uvec, final double[] Ku) {
        java.util.Arrays.fill(Ku, 0.0);
        if (this.n <= PAR_THRESHOLD) {
            applyKRange(uvec, Ku, 0, this.n);
        } else {
            FJP.invoke(new ApplyKRecursive(uvec, Ku, 0, this.n));
        }
    }

    private void applyKRange(final double[] uvec, final double[] Ku, final int start, final int end) {
        for (int i = start; i < end; i++) {
            final int i3 = 3 * i;
            final double uix = uvec[i3], uiy = uvec[i3 + 1], uiz = uvec[i3 + 2];
            for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
                final int j = this.neighbors[i][dir];
                if (j < 0) continue;
                final double Kij = this.springK[i][dir];
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

    private class ApplyKRecursive extends RecursiveAction {
        private final double[] uvec, Ku;
        private final int start, end;
        ApplyKRecursive(final double[] uvec, final double[] Ku, final int start, final int end) {
            this.uvec = uvec; this.Ku = Ku; this.start = start; this.end = end;
        }
        @Override
        protected void compute() {
            if (this.end - this.start <= PAR_THRESHOLD) {
                applyKRange(this.uvec, this.Ku, this.start, this.end);
            } else {
                final int mid = (this.start + this.end) / 2;
                invokeAll(new ApplyKRecursive(this.uvec, this.Ku, this.start, mid),
                          new ApplyKRecursive(this.uvec, this.Ku, mid, this.end));
            }
        }
    }

    private static double dot(final double[] a, final double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    public double getMaxWaterDepth() {
        double max = 0;
        for (int i = 0; i < this.n; i++) {
            if (this.blockWaterDepths[i] > max) max = this.blockWaterDepths[i];
        }
        return max;
    }

    public int blockCount() { return this.n; }
    public int hullBlockCount() { return this.hullBlockCount; }
    public int[] getFaceBlockCounts() { return this.faceBlockCounts; }
    public boolean isHullBlock(final int i) { return this.isHullBlock[i]; }
    public boolean isFaceExposed(final int i, final int dir) {
        return dir >= 0 && dir < 6 && i >= 0 && i < this.n && this.neighbors[i][dir] < 0;
    }
    public BlockPos getPosition(final int i) { return this.positions[i]; }

    public BlockPos getLocalPosition(final int i) {
        final BlockPos p = this.positions[i];
        return new BlockPos(
            p.getX() - this.bounds.minX(),
            p.getY() - this.bounds.minY(),
            p.getZ() - this.bounds.minZ()
        );
    }

    public BlockPos getWorldPosition(final int i) {
        final BlockPos p = this.positions[i];
        if (this.subLevelPose != null) {
            final Vector3d worldPos = new Vector3d();
            this.subLevelPose.transformPosition(new org.joml.Vector3d(p.getX(), p.getY(), p.getZ()), worldPos);
            return BlockPos.containing(worldPos.x, worldPos.y, worldPos.z);
        }
        if (this.orientationEnabled) {
            final Vector3d tmp = new Vector3d(p.getX(), p.getY(), p.getZ());
            this.orientation.transform(tmp);
            return BlockPos.containing(tmp.x, tmp.y, tmp.z);
        }
        return p;
    }

    public int getNeighbor(final int i, final int dir) {
        if (i < 0 || i >= this.n || dir < 0 || dir >= MAX_NEIGHBORS) return -1;
        return this.neighbors[i][dir];
    }

    public double getSpringK(final int i, final int dir) {
        if (i < 0 || i >= this.n || dir < 0 || dir >= MAX_NEIGHBORS) return 0;
        return this.springK[i][dir];
    }

    public static int getMaxNeighbors() { return MAX_NEIGHBORS; }
    public static int[] getDX() { return DX; }
    public static int[] getDY() { return DY; }
    public static int[] getDZ() { return DZ; }
    public static double[] getInvDist() { return INV_DIST; }

    public double getYoungsModulus(final int i) { return this.E[i]; }
    public double getYieldStress(final int i) { return this.yieldStress[i]; }
    public double getVonMises(final int i) { return computeVonMises(i); }
    public int getExposedFaceCount(final int i) {
        BlockPos pos = this.positions[i];
        Integer eff = this.effectiveFaceCounts.get(pos);
        return (eff != null) ? eff : this.exposedFaceCount[i];
    }
    public int getRawExposedFaceCount(final int i) { return this.exposedFaceCount[i]; }
    public double getCoherence() { return this.coherence; }
    public Map<BlockPos, Vector3d> getSmoothedNormals() { return this.smoothedNormals; }
    public double getVolumeFraction(final int i) { return this.volFraction[i]; }
    public long getSolveTimeNanos() { return this.solveTimeNanos; }
    public double[] getU() { return this.u; }
    public long getStructureHash() { return this.structureHash; }
    public BoundingBox3ic getBounds() { return this.bounds; }

    public int getDepthFromTop(final int i) {
        final Vector3d localDown = new Vector3d(0, -1, 0);
        if (this.orientationEnabled) {
            this.orientation.transform(localDown);
        }
        double minDepth = Double.POSITIVE_INFINITY, maxDepth = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < this.n; j++) {
            final BlockPos p = this.positions[j];
            final double d = p.getX() * localDown.x + p.getY() * localDown.y + p.getZ() * localDown.z;
            if (d < minDepth) minDepth = d;
            if (d > maxDepth) maxDepth = d;
        }
        final BlockPos pos = this.positions[i];
        final double depth = (pos.getX() * localDown.x + pos.getY() * localDown.y + pos.getZ() * localDown.z) - minDepth;
        return (int) Math.round(depth);
    }

    public double[] computeCrushDepth() {
        final double[] result = new double[this.n + 1];
        double globalDepth = Double.POSITIVE_INFINITY;
        int worstBlock = -1;

        for (int i = 0; i < this.n; i++) {
            if (this.yieldStress[i] <= 0) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double blockDepth = this.blockWaterDepths[i];
            if (blockDepth <= 0) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double vm = computeVonMises(i);
            if (vm <= 1e-30) {
                result[i] = Double.POSITIVE_INFINITY;
                continue;
            }
            final double depth = blockDepth * this.yieldStress[i] / vm;
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

        result[this.n] = (double) worstBlock;
        return result;
    }

    public void refreshWaterDepths(final double newWaterSurfaceWorldY, final Pose3dc currentPose) {
        this.waterSurfaceWorldY = newWaterSurfaceWorldY;
        if (currentPose != null) this.subLevelPose = currentPose;
        if (!Double.isFinite(this.waterSurfaceWorldY)) {
            java.util.Arrays.fill(this.blockWaterDepths, 0.0);
            return;
        }
        final Vector3d tmpVec = new Vector3d();
        final Vector3d worldPos = new Vector3d();
        java.util.Arrays.fill(this.blockWaterDepths, 0.0);
        for (int i = 0; i < this.n; i++) {
            final BlockPos p = this.positions[i];
            final double worldMinY = computeBlockMinWorldY(p.getX(), p.getY(), p.getZ(), tmpVec, worldPos);
            if (worldMinY >= this.waterSurfaceWorldY) continue;
            this.blockWaterDepths[i] = this.waterSurfaceWorldY - worldMinY;
        }
    }

    public void refreshWaterDepths(final double newWaterSurfaceWorldY) {
        refreshWaterDepths(newWaterSurfaceWorldY, null);
    }

    private double computeBlockMinWorldY(final int x, final int y, final int z, final Vector3d tmp, final Vector3d worldPos) {
        if (this.subLevelPose != null) {
            if (!this.orientationEnabled) {
                tmp.set(x, y, z);
                this.subLevelPose.transformPosition(tmp, worldPos);
                return worldPos.y;
            }
            double minY = Double.POSITIVE_INFINITY;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        tmp.set(x + dx, y + dy, z + dz);
                        this.subLevelPose.transformPosition(tmp, worldPos);
                        if (worldPos.y < minY) minY = worldPos.y;
                    }
                }
            }
            return minY;
        }
        if (this.orientationEnabled) {
            double minY = Double.POSITIVE_INFINITY;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        tmp.set(x + dx, y + dy, z + dz);
                        this.orientation.transform(tmp);
                        if (tmp.y < minY) minY = tmp.y;
                    }
                }
            }
            return minY;
        }
        return y;
    }

    public Map<BlockPos, Integer> getEffectiveFaceCounts() {
        return this.effectiveFaceCounts;
    }

    public double getBlockWaterDepth(final int blockIdx) {
        return this.blockWaterDepths[blockIdx];
    }

    public double[] getStressDistribution(final double waterDepth) {
        return getStressDistribution(waterDepth, computeCrushDepth());
    }

    public double[] getStressDistribution() {
        final double[] dist = new double[this.n];
        for (int i = 0; i < this.n; i++) {
            if (this.blockWaterDepths[i] <= 0) {
                dist[i] = 0;
            } else {
                final double vm = computeVonMises(i);
                if (vm <= 1e-30) {
                    dist[i] = 0;
                } else {
                    dist[i] = Math.min(1.0, vm / this.yieldStress[i]);
                }
            }
        }
        return dist;
    }

    public double getStressRatio(final int i) {
        if (this.blockWaterDepths[i] <= 0) return 0;
        final double vm = computeVonMises(i);
        if (vm <= 1e-30) return 0;
        return vm / this.yieldStress[i];
    }

    public double[] getStressDistribution(final double waterDepth, final double[] crush) {
        final double[] dist = new double[this.n];
        for (int i = 0; i < this.n; i++) {
            if (Double.isInfinite(crush[i])) {
                dist[i] = 0;
            } else {
                dist[i] = Math.min(1.0, waterDepth / crush[i]);
            }
        }
        return dist;
    }

    private double computeVonMises(final int blockIdx) {
        final double Ei = this.E[blockIdx];
        final int i3 = 3 * blockIdx;
        final double uix = this.u[i3], uiy = this.u[i3 + 1], uiz = this.u[i3 + 2];

        double gxx = 0, gxy = 0, gxz = 0;
        double gyx = 0, gyy = 0, gyz = 0;
        double gzx = 0, gzy = 0, gzz = 0;
        double denomX = 0, denomY = 0, denomZ = 0;

        for (int dir = 0; dir < MAX_NEIGHBORS; dir++) {
            final int j = this.neighbors[blockIdx][dir];
            if (j < 0) continue;
            final int j3 = 3 * j;
            final double dux = this.u[j3] - uix;
            final double duy = this.u[j3 + 1] - uiy;
            final double duz = this.u[j3 + 2] - uiz;
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

        final double nu = cfgDouble(SubmarineConfig.POISSON_RATIO, 0.25);
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

    public BlockPos getStressCenter() {
        double totalWeight = 0;
        double wx = 0, wy = 0, wz = 0;
        for (int i = 0; i < this.n; i++) {
            final double vm = computeVonMises(i);
            if (vm <= 1e-30) continue;
            final BlockPos p = this.positions[i];
            final double w = vm * vm; 
            wx += w * (p.getX() - this.bounds.minX());
            wy += w * (p.getY() - this.bounds.minY());
            wz += w * (p.getZ() - this.bounds.minZ());
            totalWeight += w;
        }
        if (totalWeight <= 0) return new BlockPos(0, 0, 0);
        return new BlockPos(
            (int) Math.round(wx / totalWeight),
            (int) Math.round(wy / totalWeight),
            (int) Math.round(wz / totalWeight)
        );
    }

    public String debugInfo() {
        double uNorm = 0;
        for (int k = 0; k < 3 * n; k++) uNorm += this.u[k] * this.u[k];
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("Solver: n=%d, hull=%d, |u|_2=%.2e, solve=%.2fms",
            n, hullBlockCount, Math.sqrt(uNorm), solveTimeNanos / 1e6));
        if (n > 0) {
            double maxStress = 0;
            int maxIdx = 0;
            for (int i = 0; i < n; i++) {
                final double vm = computeVonMises(i);
                if (vm > maxStress) { maxStress = vm; maxIdx = i; }
            }
            sb.append(String.format(", maxStress=%.2e Pa (block %d), E=%.2e Pa, yield=%.2e Pa",
                maxStress, maxIdx, E[maxIdx], yieldStress[maxIdx]));
            sb.append(String.format(", crush=%.1f m", computeCrushDepth()[maxIdx]));
        }
        return sb.toString();
    }
}
