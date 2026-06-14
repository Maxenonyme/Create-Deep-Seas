package com.maxenonyme.createsubmarine.submarine.stress;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.command.SubLevelArgumentType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.maxenonyme.createsubmarine.submarine.util.SchematicLoader;
import com.maxenonyme.createsubmarine.submarine.util.SchematicLoader.SchematicData;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class StressCommand {

    /** Default sea level Y for hydrostatic pressure computation. */
    public static final double SEA_LEVEL_Y = 63.0;

    public static void register(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        final var reqOp = Commands.literal("submarine").requires(src -> src.hasPermission(2));

        reqOp.then(Commands.literal("crushdepth")
                .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                        .executes(ctx -> executeCrushDepth(ctx))));

        reqOp.then(Commands.literal("stress")
                .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                        .executes(ctx -> executeStress(ctx, -1))
                        .then(Commands.argument("depth", IntegerArgumentType.integer(0, 11000))
                                .executes(ctx -> executeStress(ctx, IntegerArgumentType.getInteger(ctx, "depth"))))));

        reqOp.then(Commands.literal("stressdbg")
                .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                        .executes(ctx -> executeDebug(ctx))));

        reqOp.then(Commands.literal("stressprof")
                .executes(ctx -> executeProfiler(ctx)));

        reqOp.then(Commands.literal("stresstest")
                .then(Commands.argument("x", IntegerArgumentType.integer(1, 50))
                        .then(Commands.argument("y", IntegerArgumentType.integer(1, 50))
                                .then(Commands.argument("z", IntegerArgumentType.integer(1, 50))
                                        .executes(ctx -> executeStressTest(ctx, false))
                                        .then(Commands.literal("hollow")
                                                .executes(ctx -> executeStressTest(ctx, true)))))));

        reqOp.then(Commands.literal("testbattery")
                .executes(ctx -> executeTestBattery(ctx))
                .then(Commands.literal("schematic")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(ctx -> executeTestBatterySchematic(ctx))))
                .then(Commands.literal("csv")
                        .then(Commands.argument("file", StringArgumentType.string())
                                .executes(ctx -> executeTestBatteryCsv(ctx)))));

        reqOp.then(Commands.literal("shapeviz")
                .then(Commands.argument("sub_level", SubLevelArgumentType.subLevels())
                        .then(Commands.literal("all")
                                .then(Commands.literal("on")
                                        .executes(ctx -> executeShapeVizAll(ctx, true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> executeShapeVizAll(ctx, false))))
                        .then(Commands.literal("coherence")
                                .then(Commands.literal("on")
                                        .executes(ctx -> executeShapeViz(ctx, "coherence", true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> executeShapeViz(ctx, "coherence", false))))
                        .then(Commands.literal("wireframe")
                                .then(Commands.literal("on")
                                        .executes(ctx -> executeShapeViz(ctx, "wireframe", true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> executeShapeViz(ctx, "wireframe", false))))
                        .then(Commands.literal("smooth")
                                .then(Commands.literal("on")
                                        .executes(ctx -> executeShapeViz(ctx, "smooth", true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> executeShapeViz(ctx, "smooth", false))))
                        .then(Commands.literal("stresscenter")
                                .then(Commands.literal("on")
                                        .executes(ctx -> executeShapeViz(ctx, "stresscenter", true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> executeShapeViz(ctx, "stresscenter", false))))
                        .executes(ctx -> executeShapeVizStatus(ctx))));

        dispatcher.register(reqOp);
    }

    private static int executeCrushDepth(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            source.sendFailure(Component.literal("No sub-levels found"));
            return 0;
        }

        for (final ServerSubLevel subLevel : subLevels) {
            final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.getOrCreate(subLevel.getLevel());
            final String result = analyzer.getCrushDepthResult(subLevel);
            source.sendSuccess(() -> Component.literal(result), false);
        }

        return subLevels.size();
    }

    private static int executeStress(final CommandContext<CommandSourceStack> ctx, final int explicitDepth) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            source.sendFailure(Component.literal("No sub-levels found"));
            return 0;
        }

        for (final ServerSubLevel subLevel : subLevels) {
            final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.getOrCreate(subLevel.getLevel());

            int depth = explicitDepth;
            if (depth < 0) {
                final LatticeStressSolver solver = analyzer.getSolver(subLevel);
                if (solver != null && solver.blockCount() > 0) {
                    final double waterSurfaceY = SubLevelStressAnalyzer.getWaterSurfaceWorldY(subLevel);
                    solver.refreshWaterDepths(waterSurfaceY);
                    final double maxDepth = solver.getMaxWaterDepth();
                    depth = maxDepth > 0 ? (int) Math.round(maxDepth) : 0;
                } else {
                    depth = 0;
                }
            }

            final String result = analyzer.getStressResult(subLevel, depth);
            source.sendSuccess(() -> Component.literal(result), false);
        }

        return subLevels.size();
    }

    private static int executeDebug(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final Collection<ServerSubLevel> subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");

        if (subLevels.isEmpty()) {
            source.sendFailure(Component.literal("No sub-levels found"));
            return 0;
        }

        for (final ServerSubLevel subLevel : subLevels) {
            final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.getOrCreate(subLevel.getLevel());
            final LatticeStressSolver solver = analyzer.getSolver(subLevel);
            if (solver == null || solver.blockCount() == 0) {
                source.sendSuccess(() -> Component.literal("No solver for this sub-level"), false);
                continue;
            }
            source.sendSuccess(() -> Component.literal("=== Stress Debug ==="), false);
            source.sendSuccess(() -> Component.literal(solver.debugInfo()), false);

            final double[] stress = solver.getStressDistribution();
            final double[] crushDepths = solver.computeCrushDepth();

            int highestIdx = -1, lowestIdx = -1;
            double highestStress = -1, lowestStress = Double.POSITIVE_INFINITY;
            for (int i = 0; i < solver.blockCount(); i++) {
                if (stress[i] <= 0) continue;
                if (stress[i] > highestStress) { highestStress = stress[i]; highestIdx = i; }
                if (stress[i] < lowestStress) { lowestStress = stress[i]; lowestIdx = i; }
            }

            final double finalHighestStress = highestStress;
            final double finalLowestStress = lowestStress;

            if (highestIdx >= 0) {
                final int hi = highestIdx;
                source.sendSuccess(() -> Component.literal(String.format(
                    "Highest stress: block %d | sable=%s world=%s local=%s | stress=%.2e%% crush=%.1f bl [%s]",
                    hi,
                    solver.getPosition(hi).toShortString(),
                    solver.getWorldPosition(hi).toShortString(),
                    solver.getLocalPosition(hi).toShortString(),
                    finalHighestStress * 100, crushDepths[hi],
                    solver.isHullBlock(hi) ? "HULL" : "IN")), false);
            }

            if (lowestIdx >= 0 && lowestIdx != highestIdx) {
                final int li = lowestIdx;
                source.sendSuccess(() -> Component.literal(String.format(
                    "Lowest stress:  block %d | sable=%s world=%s local=%s | stress=%.2e%% crush=%.1f bl [%s]",
                    li,
                    solver.getPosition(li).toShortString(),
                    solver.getWorldPosition(li).toShortString(),
                    solver.getLocalPosition(li).toShortString(),
                    finalLowestStress * 100, crushDepths[li],
                    solver.isHullBlock(li) ? "HULL" : "IN")), false);
            }

            final double coherence = solver.getCoherence();
            final boolean useAnalytical = ShapeClassifier.useAnalytical(coherence, 999, 0);
            final boolean useCorrected = ShapeClassifier.useCorrected(coherence);
            final int normalsCount = solver.getSmoothedNormals().size();
            final int effCountsCount = solver.getEffectiveFaceCounts().size();
            source.sendSuccess(() -> Component.literal(String.format(
                "Smoothing: coherence=%.3f (analytical=%s, corrected=%s, roughnessPenalty=%.3f) | kernel=%.1f bl | normals=%d | effFaces=%d",
                coherence, useAnalytical, useCorrected,
                ShapeClassifier.roughnessPenalty(coherence),
                SubmarineConfig.KERNEL_RADIUS.get(),
                normalsCount, effCountsCount)), false);
        }

        return subLevels.size();
    }

    private static int executeProfiler(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        for (final net.minecraft.server.level.ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            final SubLevelStressAnalyzer analyzer = SubLevelStressAnalyzer.INSTANCES.get(level);
            if (analyzer == null) continue;
            source.sendSuccess(() -> Component.literal(analyzer.getProfilerResult()), false);
        }
        return 1;
    }

    private static int executeShapeViz(final CommandContext<CommandSourceStack> ctx,
                                        final String mode, final boolean value) throws CommandSyntaxException {
        final var player = ctx.getSource().getPlayerOrException();
        final var subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");
        for (final var sub : subLevels) {
            PacketDistributor.sendToPlayer(player,
                new com.maxenonyme.createsubmarine.submarine.network.ShapeVizPayload(sub.getUniqueId(), mode, value));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Shape viz [" + mode + "] set to " + value + " for " + subLevels.size() + " sub-level(s)"), false);
        return subLevels.size();
    }

    private static int executeShapeVizStatus(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final var subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Shape viz enabled for " + subLevels.size() + " sub-level(s). Use on/off sub-commands to toggle."), false);
        return subLevels.size();
    }

    private static int executeStressTest(final CommandContext<CommandSourceStack> ctx, final boolean hollow) throws CommandSyntaxException {
        final int sx = IntegerArgumentType.getInteger(ctx, "x");
        final int sy = IntegerArgumentType.getInteger(ctx, "y");
        final int sz = IntegerArgumentType.getInteger(ctx, "z");
        final CommandSourceStack source = ctx.getSource();
        final int originX = 0, originY = 0, originZ = 0;

        source.sendSuccess(() -> Component.literal("Creating " + sx + "x" + sy + "x" + sz + " " +
            (hollow ? "hollow" : "solid") + " test grid..."), false);

        final BlockGetter mockLevel = new BlockGetter() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                int lx = pos.getX() - originX;
                int ly = pos.getY() - originY;
                int lz = pos.getZ() - originZ;
                if (lx < 0 || lx >= sx || ly < 0 || ly >= sy || lz < 0 || lz >= sz) {
                    return Blocks.AIR.defaultBlockState();
                }
                if (hollow && lx > 0 && lx < sx - 1 && ly > 0 && ly < sy - 1 && lz > 0 && lz < sz - 1) {
                    return Blocks.AIR.defaultBlockState();
                }
                return Blocks.STONE.defaultBlockState();
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }

            @Override
            public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) { return Optional.empty(); }

            @Override
            public int getHeight() { return sy; }

            @Override
            public int getMinBuildHeight() { return originY; }

            @Override
            public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        };

        final var bounds = new BoundingBox3i(originX, originY, originZ, originX + sx - 1, originY + sy - 1, originZ + sz - 1);

        final long t0 = System.nanoTime();
        final LatticeStressSolver solver = new LatticeStressSolver(mockLevel, bounds,
            null, null, null, null, SEA_LEVEL_Y);
        final long solveTime = System.nanoTime() - t0;

        if (solver.blockCount() == 0) {
            source.sendFailure(Component.literal("Test failed: no blocks in the solver"));
            return 0;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("=== Stress Test ").append(sx).append("x").append(sy).append("x").append(sz);
        sb.append(hollow ? " (hollow)" : " (solid)").append(" ===\n");
        sb.append(String.format("Blocks: %d (hull: %d) | Solve: %.2fms\n",
            solver.blockCount(), solver.hullBlockCount(), solveTime / 1e6));

        final BlockPos stressCenter = solver.getStressCenter();
        sb.append(String.format("Stress center (local): %s\n", stressCenter.toShortString()));

        final double[] crushDepths = solver.computeCrushDepth();
        final double[] stressDist = solver.getStressDistribution(64.0, crushDepths);

        double maxFrac = 0, sumFrac = 0;
        int maxIdx = -1;
        for (int i = 0; i < solver.blockCount(); i++) {
            if (stressDist[i] > maxFrac) { maxFrac = stressDist[i]; maxIdx = i; }
            sumFrac += stressDist[i];
        }
        final double avgFrac = solver.blockCount() > 0 ? sumFrac / solver.blockCount() : 0;
        sb.append(String.format("Avg stress (at 64m): %.1f%% | Max stress: %.1f%% (block %d)\n",
            avgFrac * 100, maxFrac * 100, maxIdx));

        Integer[] indices = new Integer[solver.blockCount()];
        for (int i = 0; i < solver.blockCount(); i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Double.compare(stressDist[b], stressDist[a]));

        final int topN = Math.min(5, solver.blockCount());
        sb.append("Top stressed blocks:\n");
        for (int k = 0; k < topN; k++) {
            int idx = indices[k];
            if (stressDist[idx] <= 0) break;
            BlockPos pos = solver.getLocalPosition(idx);
            sb.append(String.format("  [%d] at %s: stress=%.1f%%, hull=%s, E=%.1e, yield=%.1e, crush=%.1f bl\n",
                idx, pos.toShortString(), stressDist[idx] * 100,
                solver.isHullBlock(idx) ? "YES" : "no",
                solver.getYoungsModulus(idx), solver.getYieldStress(idx),
                crushDepths[idx]));
        }

        int worstBlock = crushDepths.length > 0 ? (int) crushDepths[solver.blockCount()] : -1;
        double minCrush = worstBlock >= 0 ? crushDepths[worstBlock] : Double.POSITIVE_INFINITY;
        sb.append(String.format("Min crush depth: %.1f blocks (weakest at block %d)\n", minCrush, worstBlock));

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int executeTestBattery(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Running full test battery (seaLevelY=" + (int)SEA_LEVEL_Y + ", RHO_G=" + (int)LatticeStressSolver.RHO_G + ")..."), false);

        final java.io.File outputFile = new java.io.File("test_battery.csv");
        final boolean exists = outputFile.exists();
        try (final java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileOutputStream(outputFile, true))) {
            if (!exists) {
                pw.println("shape,size,orientation,yaw,pitch,roll,blocks,hull_blocks,solve_time_ms," +
                    "avg_stress_pct,max_stress_pct,min_crush_depth_blocks," +
                    "stress_center_x,stress_center_y,stress_center_z,hull_ratio," +
                    "material,thickness,internal_struct");
            }

            final BlockState[] materials = {
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.DIAMOND_BLOCK.defaultBlockState()
            };
            final String[] matNames = {"oak","iron","diamond"};

            //for testing
            final int S = 10; // base size

            for (int mi = 0; mi < materials.length; mi++) {
                final BlockState mat = materials[mi];
                final String matName = matNames[mi];

                for (int thick0 = 1; thick0 <= 5; thick0++) {
                    final int thick = thick0;
                    final int ox = 0, oy = 0, oz = 0;
                    final int sx = S, sy = S, sz = S;
                    final int s1 = S - 1;

                    // Solid box of size S
                    runOneTest(pw, mat, matName, "box", S, thick, "solid",
                        ox, oy, oz, sx, sy, sz,
                        (lx,ly,lz) -> true, source);

                    // Hollow box of size S, thickness T
                    runOneTest(pw, mat, matName, "box", S, thick, "hollow",
                        ox, oy, oz, sx, sy, sz,
                        (lx,ly,lz) -> {
                            int dx = Math.min(lx, s1-lx);
                            int dy = Math.min(ly, s1-ly);
                            int dz = Math.min(lz, s1-lz);
                            return Math.min(Math.min(dx, dy), dz) < thick;
                        }, source);

                    // Box with rib structure
                    runOneTest(pw, mat, matName, "box", S, thick, "ribs",
                        ox, oy, oz, sx, sy, sz,
                        (lx,ly,lz) -> {
                            int dx = Math.min(lx, s1-lx);
                            int dy = Math.min(ly, s1-ly);
                            int dz = Math.min(lz, s1-lz);
                            if (Math.min(Math.min(dx, dy), dz) < thick) return true;
                            return lx % 4 == 0 && lz % 4 == 0;
                        }, source);
                }
            }

            // Sphere and ellipsoid tests (solid only, with fixed material)
            for (int mi = 0; mi < materials.length; mi++) {
                final BlockState mat = materials[mi];
                final String matName = matNames[mi];

                // Sphere radius 5
                runOneTestSphere(pw, mat, matName, "sphere", 5,
                    (lx,ly,lz) -> {
                        double dx = lx - 4.5, dy = ly - 4.5, dz = lz - 4.5;
                        return dx*dx + dy*dy + dz*dz <= 5.0*5.0;
                    }, 0, 0, 0, 9, source);

                // Ellipsoid 2:1:1
                runOneTestSphere(pw, mat, matName, "ellipsoid_211", 5,
                    (lx,ly,lz) -> {
                        double dx = (lx - 4.5)/2.0, dy = ly - 4.5, dz = lz - 4.5;
                        return dx*dx + dy*dy + dz*dz <= 1.0;
                    }, 0, 0, 0, 9, source);

                // Ellipsoid 1:2:1
                runOneTestSphere(pw, mat, matName, "ellipsoid_121", 5,
                    (lx,ly,lz) -> {
                        double dx = lx - 4.5, dy = (ly - 4.5)/2.0, dz = lz - 4.5;
                        return dx*dx + dy*dy + dz*dz <= 1.0;
                    }, 0, 0, 0, 9, source);

                // Ellipsoid 4:1:1
                runOneTestSphere(pw, mat, matName, "ellipsoid_411", 5,
                    (lx,ly,lz) -> {
                        double dx = (lx - 4.5)/4.0, dy = ly - 4.5, dz = lz - 4.5;
                        return dx*dx + dy*dy + dz*dz <= 1.0;
                    }, 0, 0, 0, 9, source);
            }

            //for testing
            source.sendSuccess(() -> Component.literal("Test battery complete! Results written to: " +
                outputFile.getAbsolutePath()), false);
        } catch (java.io.IOException e) {
            source.sendFailure(Component.literal("IO error: " + e.getMessage()));
        }
        return 1;
    }

    private static int executeTestBatterySchematic(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final String pathStr = StringArgumentType.getString(ctx, "path");
        final File path = new File(pathStr);
        if (!path.exists()) {
            source.sendFailure(Component.literal("Path not found: " + pathStr));
            return 0;
        }

        final File[] schemFiles;
        if (path.isDirectory()) {
            schemFiles = path.listFiles((dir, name) -> name.endsWith(".schem"));
            if (schemFiles == null || schemFiles.length == 0) {
                source.sendFailure(Component.literal("No .schem files found in " + pathStr));
                return 0;
            }
        } else {
            schemFiles = new File[]{path};
        }

        source.sendSuccess(() -> Component.literal("Running schematic test battery on " + schemFiles.length + " file(s)..."), false);

        final int[] passed = {0};
        final int[] failed = {0};
        for (File f : schemFiles) {
            final File curFile = f;
            try {
                SchematicData data = SchematicLoader.load(curFile);
                BlockGetter bg = SchematicLoader.toBlockGetter(data);
                BoundingBox3i bounds = new BoundingBox3i(
                    data.offset()[0], data.offset()[1], data.offset()[2],
                    data.offset()[0] + data.width() - 1,
                    data.offset()[1] + data.height() - 1,
                    data.offset()[2] + data.length() - 1
                );

                final long t0 = System.nanoTime();
                final LatticeStressSolver solver = new LatticeStressSolver(bg, bounds,
                    null, null, null, null, SEA_LEVEL_Y);
                final long solveTime = System.nanoTime() - t0;

                if (solver.blockCount() == 0) {
                    source.sendSuccess(() -> Component.literal(
                        "  [SKIP] " + curFile.getName() + " (air)"), false);
                    continue;
                }

                final double[] cd = solver.computeCrushDepth();
                final int worstBlock = (int) cd[solver.blockCount()];
                final double minCrush = worstBlock >= 0 ? cd[worstBlock] : Double.POSITIVE_INFINITY;

                source.sendSuccess(() -> Component.literal(String.format(
                    "  [OK] %s: %d blocks, hull=%d, solve=%.1fms, crush=%.1f bl%s",
                    curFile.getName(),
                    solver.blockCount(), solver.hullBlockCount(),
                    solveTime / 1e6, minCrush,
                    solver.debugInfo())), false);
                passed[0]++;
            } catch (Exception e) {
                source.sendFailure(Component.literal("  [FAIL] " + curFile.getName() + ": " + e.getMessage()));
                failed[0]++;
            }
        }
        final int finalPassed = passed[0];
        final int finalFailed = failed[0];
        source.sendSuccess(() -> Component.literal(String.format(
            "Schematic test complete: %d passed, %d failed", finalPassed, finalFailed)), false);
        return finalPassed;
    }

    private static int executeTestBatteryCsv(final CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        final CommandSourceStack source = ctx.getSource();
        final String csvPath = StringArgumentType.getString(ctx, "file");
        final File file = new File(csvPath);

        // Scan test_shapes/ and schematics/ directories
        final java.util.List<File> schemFiles = new java.util.ArrayList<>();
        for (String dir : new String[]{"test_shapes", "schematics"}) {
            File d = new File(dir);
            if (d.exists() && d.isDirectory()) {
                File[] files = d.listFiles((f, name) -> name.endsWith(".schem"));
                if (files != null) java.util.Collections.addAll(schemFiles, files);
            }
        }

        if (schemFiles.isEmpty()) {
            source.sendFailure(Component.literal(
                "No .schem files found in test_shapes/ or schematics/ directories.\n" +
                "Run: python test_shapes/generate_test_shapes.py to generate them."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
            "Running CSV battery on " + schemFiles.size() + " schematic(s) -> " + csvPath), false);

        final boolean exists = file.exists();
        try (final PrintWriter pw = new PrintWriter(new FileOutputStream(file, true))) {
            if (!exists) {
                pw.println("file,shape_desc,blocks,hull_blocks,solve_time_ms," +
                    "avg_stress_pct,max_stress_pct,min_crush_depth_blocks," +
                    "stress_center_x,stress_center_y,stress_center_z,hull_ratio");
            }

            for (File f : schemFiles) {
                try {
                    SchematicData data = SchematicLoader.load(f);
                    BlockGetter bg = SchematicLoader.toBlockGetter(data);
                    BoundingBox3i bounds = new BoundingBox3i(
                        data.offset()[0], data.offset()[1], data.offset()[2],
                        data.offset()[0] + data.width() - 1,
                        data.offset()[1] + data.height() - 1,
                        data.offset()[2] + data.length() - 1
                    );

                    final long t0 = System.nanoTime();
                    final LatticeStressSolver solver = new LatticeStressSolver(bg, bounds,
                        null, null, null, null, SEA_LEVEL_Y);
                    final long solveTime = System.nanoTime() - t0;

                    if (solver.blockCount() == 0) continue;

                    final double[] cd = solver.computeCrushDepth();
                    final double[] sd = solver.getStressDistribution(64.0, cd);

                    double maxFrac = 0, sumFrac = 0;
                    for (int i = 0; i < solver.blockCount(); i++) {
                        if (sd[i] > maxFrac) maxFrac = sd[i];
                        sumFrac += sd[i];
                    }
                    final double avgFrac = solver.blockCount() > 0 ? sumFrac / solver.blockCount() : 0;
                    final int worstBlock = (int) cd[solver.blockCount()];
                    final double minCrush = worstBlock >= 0 ? cd[worstBlock] : Double.POSITIVE_INFINITY;
                    final BlockPos center = solver.getStressCenter();
                    final double hullRatio = (double) solver.hullBlockCount() / solver.blockCount();

                    String desc = f.getName().replace(".schem", "");
                    pw.printf("%s,%s,%d,%d,%.3f,%.4f,%.4f,%.1f,%s,%s,%s,%.4f%n",
                        f.getName(), desc,
                        solver.blockCount(), solver.hullBlockCount(),
                        solveTime / 1e6,
                        avgFrac * 100, maxFrac * 100,
                        minCrush,
                        formatCoord(center.getX()), formatCoord(center.getY()), formatCoord(center.getZ()),
                        hullRatio);
                } catch (Exception e) {
                    source.sendFailure(Component.literal("  Error on " + f.getName() + ": " + e.getMessage()));
                }
            }
            source.sendSuccess(() -> Component.literal("CSV written to: " + file.getAbsolutePath()), false);
        } catch (java.io.IOException e) {
            source.sendFailure(Component.literal("IO error: " + e.getMessage()));
        }
        return 1;
    }

    @FunctionalInterface
    private interface BlockPredicate {
        boolean isSolid(int lx, int ly, int lz);
    }

    private static void runOneTest(final java.io.PrintWriter pw, final BlockState mat, final String matName,
                                    final String shape, final int size, final int thickness,
                                    final String internalStruct,
                                    final int ox, final int oy, final int oz,
                                    final int sx, final int sy, final int sz,
                                    final BlockPredicate pred, final CommandSourceStack source) {
        final BoundingBox3i bounds = new BoundingBox3i(ox, oy, oz, ox + sx - 1, oy + sy - 1, oz + sz - 1);
        final BlockGetter mockLevel = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) {
                int lx = pos.getX() - ox;
                int ly = pos.getY() - oy;
                int lz = pos.getZ() - oz;
                if (lx < 0 || lx >= sx || ly < 0 || ly >= sy || lz < 0 || lz >= sz)
                    return Blocks.AIR.defaultBlockState();
                return pred.isSolid(lx, ly, lz) ? mat : Blocks.AIR.defaultBlockState();
            }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) { return Optional.empty(); }
            @Override public int getHeight() { return sy; }
            @Override public int getMinBuildHeight() { return oy; }
            @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        };

        final long t0 = System.nanoTime();
        final LatticeStressSolver solver = new LatticeStressSolver(mockLevel, bounds,
            null, null, null, null, SEA_LEVEL_Y);
        final long solveTime = System.nanoTime() - t0;
        if (solver.blockCount() == 0) return;

        final double[] crushDepths = solver.computeCrushDepth();
        final double[] stressDist = solver.getStressDistribution(64.0, crushDepths);
        double maxFrac = 0, sumFrac = 0;
        for (int i = 0; i < solver.blockCount(); i++) {
            if (stressDist[i] > maxFrac) maxFrac = stressDist[i];
            sumFrac += stressDist[i];
        }
        final double avgFrac = solver.blockCount() > 0 ? sumFrac / solver.blockCount() : 0;

        final int worstBlock = (int) crushDepths[solver.blockCount()];
        final double minCrush = worstBlock >= 0 ? crushDepths[worstBlock] : Double.POSITIVE_INFINITY;
        final BlockPos center = solver.getStressCenter();
        final double hullRatio = (double) solver.hullBlockCount() / solver.blockCount();

        pw.printf("%s,%d,identity,0,0,0,%d,%d,%.3f,%.4f,%.4f,%.1f,%s,%s,%s,%.4f,%s,%d,%s%n",
            shape, size,
            solver.blockCount(), solver.hullBlockCount(),
            solveTime / 1e6,
            avgFrac * 100, maxFrac * 100,
            minCrush,
            formatCoord(center.getX()), formatCoord(center.getY()), formatCoord(center.getZ()),
            hullRatio,
            matName, thickness, internalStruct);
    }

    private static void runOneTestSphere(final java.io.PrintWriter pw, final BlockState mat, final String matName,
                                          final String shape, final int radius,
                                          final BlockPredicate pred,
                                          final int ox, final int oy, final int oz,
                                          final int size, final CommandSourceStack source) {
        final int s = 2 * radius;
        final BoundingBox3i bounds = new BoundingBox3i(ox, oy, oz, ox + s, oy + s, oz + s);
        final BlockGetter mockLevel = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) {
                int lx = pos.getX() - ox;
                int ly = pos.getY() - oy;
                int lz = pos.getZ() - oz;
                if (lx < 0 || lx > s || ly < 0 || ly > s || lz < 0 || lz > s)
                    return Blocks.AIR.defaultBlockState();
                return pred.isSolid(lx, ly, lz) ? mat : Blocks.AIR.defaultBlockState();
            }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) { return Optional.empty(); }
            @Override public int getHeight() { return s + 1; }
            @Override public int getMinBuildHeight() { return oy; }
            @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        };

        final long t0 = System.nanoTime();
        final LatticeStressSolver solver = new LatticeStressSolver(mockLevel, bounds,
            null, null, null, null, SEA_LEVEL_Y);
        final long solveTime = System.nanoTime() - t0;
        if (solver.blockCount() == 0) return;

        final double[] crushDepths = solver.computeCrushDepth();
        final double[] stressDist = solver.getStressDistribution(64.0, crushDepths);
        double maxFrac = 0, sumFrac = 0;
        for (int i = 0; i < solver.blockCount(); i++) {
            if (stressDist[i] > maxFrac) maxFrac = stressDist[i];
            sumFrac += stressDist[i];
        }
        final double avgFrac = solver.blockCount() > 0 ? sumFrac / solver.blockCount() : 0;

        final int worstBlock = (int) crushDepths[solver.blockCount()];
        final double minCrush = worstBlock >= 0 ? crushDepths[worstBlock] : Double.POSITIVE_INFINITY;
        final BlockPos center = solver.getStressCenter();
        final double hullRatio = (double) solver.hullBlockCount() / solver.blockCount();

        pw.printf("%s,%d,identity,0,0,0,%d,%d,%.3f,%.4f,%.4f,%.1f,%s,%s,%s,%.4f,%s,1,solid%n",
            shape, radius,
            solver.blockCount(), solver.hullBlockCount(),
            solveTime / 1e6,
            avgFrac * 100, maxFrac * 100,
            minCrush,
            formatCoord(center.getX()), formatCoord(center.getY()), formatCoord(center.getZ()),
            hullRatio,
            matName);
    }

    private static String formatCoord(final int v) { return String.format("%.2f", (double) v); }

    private static int executeShapeVizAll(final CommandContext<CommandSourceStack> ctx,
                                           final boolean value) throws CommandSyntaxException {
        final var player = ctx.getSource().getPlayerOrException();
        final var subLevels = SubLevelArgumentType.getSubLevels(ctx, "sub_level");
        for (final var sub : subLevels) {
            final var id = sub.getUniqueId();
            PacketDistributor.sendToPlayer(player,
                new com.maxenonyme.createsubmarine.submarine.network.ShapeVizPayload(id, "stresscenter", value));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            "Stress center viz set to " + value + " for " + subLevels.size() + " sub-level(s)"), false);
        return subLevels.size();
    }
}
