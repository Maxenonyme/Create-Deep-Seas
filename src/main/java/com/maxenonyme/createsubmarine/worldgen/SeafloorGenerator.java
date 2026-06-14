package com.maxenonyme.createsubmarine.worldgen;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class SeafloorGenerator {
    static final int TILE_SIZE = 256;
    private static final int SEA_LEVEL = 700;

    private static final int DEEP_HEIGHT = 200;
    private static final int SHALLOW_HEIGHT = 500;

    private static final double NOISE_SCALE = 0.001;
    private static final int SHELF_CELL_SIZE = 45;
    private static final double SHELF_VARIATION = 100;

    private static final double PLAINS_EROSION = 3.5;
    private static final double SHELF_EROSION = 2.0;
    private static final double SHALLOWS_EROSION = 0.3;

    private static final double SHELF_BOUNDARY = 0.15;

    private static final int SEAMOUNT_CELL = 300;
    private static final int SEAMOUNT_RADIUS = 120;
    private static final int SEAMOUNT_MIN_H = 50;
    private static final int SEAMOUNT_MAX_H = 200;

    private static final long BASE_SEED = 420691337L;

    private static final ConcurrentHashMap<Long, short[]> sharedHeightCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, short[]> sharedNoiseCache = new ConcurrentHashMap<>();

    private static PerlinNoise terrainNoise;
    private static PerlinNoise shelfDetailNoise;

    private static PerlinNoise getTerrainNoise() {
        PerlinNoise n = terrainNoise;
        if (n == null) {
            synchronized (SeafloorGenerator.class) {
                n = terrainNoise;
                if (n == null) {
                    n = new PerlinNoise(BASE_SEED, 6, 2.0, 0.5);
                    terrainNoise = n;
                }
            }
        }
        return n;
    }

    private static PerlinNoise getShelfDetailNoise() {
        PerlinNoise n = shelfDetailNoise;
        if (n == null) {
            synchronized (SeafloorGenerator.class) {
                n = shelfDetailNoise;
                if (n == null) {
                    n = new PerlinNoise(BASE_SEED + 7, 6, 2.0, 0.5);
                    shelfDetailNoise = n;
                }
            }
        }
        return n;
    }

    private static long tileKey(int tileX, int tileZ) {
        return ((long) tileX << 32) | (tileZ & 0xFFFFFFFFL);
    }

    public static short[] getOrGenerateTile(int tileX, int tileZ) {
        long key = tileKey(tileX, tileZ);
        return sharedHeightCache.computeIfAbsent(key, k -> {
            TileData td = computeTileData(tileX, tileZ);
            sharedNoiseCache.put(k, td.noises);
            return td.heights;
        });
    }

    public static double getNoiseAt(int wx, int wz) {
        int tileX = Math.floorDiv(wx, TILE_SIZE);
        int tileZ = Math.floorDiv(wz, TILE_SIZE);
        long key = tileKey(tileX, tileZ);

        short[] noises = sharedNoiseCache.get(key);
        if (noises == null) {
            getOrGenerateTile(tileX, tileZ);
            noises = sharedNoiseCache.get(key);
            if (noises == null) {
                TileData td = computeTileData(tileX, tileZ);
                sharedNoiseCache.put(key, td.noises);
                noises = td.noises;
            }
        }

        int lx = wx - tileX * TILE_SIZE;
        int lz = wz - tileZ * TILE_SIZE;
        return noises[lz * TILE_SIZE + lx] / 32767.0;
    }

    private static record TileData(short[] heights, short[] noises) {}

    private static TileData computeTileData(int tileX, int tileZ) {
        PerlinNoise terrain = getTerrainNoise();
        PerlinNoise shelfDetail = getShelfDetailNoise();

        short[] heights = new short[TILE_SIZE * TILE_SIZE];
        short[] noises = new short[TILE_SIZE * TILE_SIZE];
        int baseX = tileX * TILE_SIZE;
        int baseZ = tileZ * TILE_SIZE;

        double[][][] shelfCellData = computeShelfCells(baseX, baseZ);
        double[][] seamountData = computeSeamounts(baseX, baseZ);

        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;

                double c = terrain.fbmErodedWithZone(wx * NOISE_SCALE, wz * NOISE_SCALE);
                c = Math.max(-1.0, Math.min(1.0, c));

                double t = (c + 1.0) / 2.0;
                double height = DEEP_HEIGHT + t * (SHALLOW_HEIGHT - DEEP_HEIGHT);

                double absC = Math.abs(c);
                if (absC < SHELF_BOUNDARY) {
                    double shelfFactor = 1.0 - absC / SHELF_BOUNDARY;
                    shelfFactor = shelfFactor * shelfFactor * (3 - 2 * shelfFactor);

                    double bestCellHeight = shelfCellData[lz][lx][0];
                    double edgeDist = shelfCellData[lz][lx][1];

                    double cellBlend = 1.0 - Math.min(1.0, edgeDist / (SHELF_CELL_SIZE * 0.35));
                    cellBlend = Math.max(0, cellBlend);
                    cellBlend = cellBlend * cellBlend;

                    height += bestCellHeight * cellBlend * shelfFactor;

                    double rough = shelfDetail.fbmEroded(wx * 0.03, wz * 0.03, 2.0) * 5 * cellBlend * shelfFactor;
                    height += rough;
                }

                if (absC > SHELF_BOUNDARY) {
                    height += seamountData[lz][lx];
                }

                int finalHeight = (int) Math.round(height);
                finalHeight = Math.max(1, Math.min(finalHeight, SEA_LEVEL - 50));
                heights[lz * TILE_SIZE + lx] = (short) finalHeight;
                noises[lz * TILE_SIZE + lx] = (short) (c * 32767);
            }
        }
        return new TileData(heights, noises);
    }

    public static short[] generateTile(int tileX, int tileZ) {
        return getOrGenerateTile(tileX, tileZ);
    }

    public static int getHeightAt(int wx, int wz) {
        int tileX = Math.floorDiv(wx, TILE_SIZE);
        int tileZ = Math.floorDiv(wz, TILE_SIZE);
        short[] tile = getOrGenerateTile(tileX, tileZ);
        int lx = wx - tileX * TILE_SIZE;
        int lz = wz - tileZ * TILE_SIZE;
        return tile[lz * TILE_SIZE + lx] & 0xFFFF;
    }

    private static double[] getNearestShelfCell(int wx, int wz) {
        int minCellX = Math.floorDiv(wx - SHELF_CELL_SIZE, SHELF_CELL_SIZE);
        int maxCellX = Math.floorDiv(wx + SHELF_CELL_SIZE, SHELF_CELL_SIZE);
        int minCellZ = Math.floorDiv(wz - SHELF_CELL_SIZE, SHELF_CELL_SIZE);
        int maxCellZ = Math.floorDiv(wz + SHELF_CELL_SIZE, SHELF_CELL_SIZE);

        double bestHeight = 0;
        double nearestDist2 = Double.MAX_VALUE;

        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                long hash = (cx * 374761393L) ^ (cz * 668265263L) ^ (BASE_SEED + 17);
                Random rng = new Random(hash);
                double centerX = cx * SHELF_CELL_SIZE + rng.nextDouble() * SHELF_CELL_SIZE;
                double centerZ = cz * SHELF_CELL_SIZE + rng.nextDouble() * SHELF_CELL_SIZE;
                double d2 = (wx - centerX) * (wx - centerX) + (wz - centerZ) * (wz - centerZ);
                if (d2 < nearestDist2) {
                    nearestDist2 = d2;
                    Random hRng = new Random(hash + 12345L);
                    bestHeight = (hRng.nextDouble() - 0.5) * 2.0 * SHELF_VARIATION;
                }
            }
        }
        return new double[]{bestHeight, Math.sqrt(nearestDist2)};
    }

    private static double getNearestSeamountHeight(int wx, int wz) {
        int minCellX = Math.floorDiv(wx - SEAMOUNT_CELL, SEAMOUNT_CELL);
        int maxCellX = Math.floorDiv(wx + SEAMOUNT_CELL, SEAMOUNT_CELL);
        int minCellZ = Math.floorDiv(wz - SEAMOUNT_CELL, SEAMOUNT_CELL);
        int maxCellZ = Math.floorDiv(wz + SEAMOUNT_CELL, SEAMOUNT_CELL);

        double nearest = Double.MAX_VALUE;
        double nearestH = 0;

        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                long hash = (cx * 374761393L) ^ (cz * 668265263L) ^ (BASE_SEED + 3);
                Random rng = new Random(hash);
                double cxPos = cx * SEAMOUNT_CELL + rng.nextDouble() * SEAMOUNT_CELL;
                double czPos = cz * SEAMOUNT_CELL + rng.nextDouble() * SEAMOUNT_CELL;
                double d2 = (wx - cxPos) * (wx - cxPos) + (wz - czPos) * (wz - czPos);
                if (d2 < nearest) {
                    nearest = d2;
                    nearestH = SEAMOUNT_MIN_H + rng.nextDouble() * (SEAMOUNT_MAX_H - SEAMOUNT_MIN_H);
                }
            }
        }

        double dist = Math.sqrt(nearest);
        if (dist >= SEAMOUNT_RADIUS) return 0;
        double s = 1.0 - dist / SEAMOUNT_RADIUS;
        return nearestH * (s * s * (3 - 2 * s));
    }

    private static double[][][] computeShelfCells(int baseX, int baseZ) {
        double[][][] data = new double[TILE_SIZE][TILE_SIZE][2];
        int minCellX = Math.floorDiv(baseX - SHELF_CELL_SIZE, SHELF_CELL_SIZE);
        int maxCellX = Math.floorDiv(baseX + TILE_SIZE + SHELF_CELL_SIZE, SHELF_CELL_SIZE);
        int minCellZ = Math.floorDiv(baseZ - SHELF_CELL_SIZE, SHELF_CELL_SIZE);
        int maxCellZ = Math.floorDiv(baseZ + TILE_SIZE + SHELF_CELL_SIZE, SHELF_CELL_SIZE);

        java.util.List<double[]> cells = new java.util.ArrayList<>();
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                long hash = (cx * 374761393L) ^ (cz * 668265263L) ^ (BASE_SEED + 17);
                Random rng = new Random(hash);
                double centerX = cx * SHELF_CELL_SIZE + rng.nextDouble() * SHELF_CELL_SIZE;
                double centerZ = cz * SHELF_CELL_SIZE + rng.nextDouble() * SHELF_CELL_SIZE;
                Random hRng = new Random(hash + 12345L);
                double cellHeight = (hRng.nextDouble() - 0.5) * 2.0 * SHELF_VARIATION;
                cells.add(new double[]{centerX, centerZ, cellHeight});
            }
        }

        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                double bestHeight = 0;
                double nearestDist2 = Double.MAX_VALUE;

                for (double[] cell : cells) {
                    double d2 = (wx - cell[0]) * (wx - cell[0]) + (wz - cell[1]) * (wz - cell[1]);
                    if (d2 < nearestDist2) {
                        nearestDist2 = d2;
                        bestHeight = cell[2];
                    }
                }
                data[lz][lx][0] = bestHeight;
                data[lz][lx][1] = Math.sqrt(nearestDist2);
            }
        }
        return data;
    }

    private static double[][] computeSeamounts(int baseX, int baseZ) {
        double[][] data = new double[TILE_SIZE][TILE_SIZE];
        int minCellX = Math.floorDiv(baseX - SEAMOUNT_CELL, SEAMOUNT_CELL);
        int maxCellX = Math.floorDiv(baseX + TILE_SIZE + SEAMOUNT_CELL, SEAMOUNT_CELL);
        int minCellZ = Math.floorDiv(baseZ - SEAMOUNT_CELL, SEAMOUNT_CELL);
        int maxCellZ = Math.floorDiv(baseZ + TILE_SIZE + SEAMOUNT_CELL, SEAMOUNT_CELL);

        java.util.List<double[]> cells = new java.util.ArrayList<>();
        for (int cx = minCellX; cx <= maxCellX; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                long hash = (cx * 374761393L) ^ (cz * 668265263L) ^ (BASE_SEED + 3);
                Random rng = new Random(hash);
                double cxPos = cx * SEAMOUNT_CELL + rng.nextDouble() * SEAMOUNT_CELL;
                double czPos = cz * SEAMOUNT_CELL + rng.nextDouble() * SEAMOUNT_CELL;
                double h = SEAMOUNT_MIN_H + rng.nextDouble() * (SEAMOUNT_MAX_H - SEAMOUNT_MIN_H);
                cells.add(new double[]{cxPos, czPos, h});
            }
        }

        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                double nearest = Double.MAX_VALUE;
                double nearestH = 0;

                for (double[] cell : cells) {
                    double d2 = (wx - cell[0]) * (wx - cell[0]) + (wz - cell[1]) * (wz - cell[1]);
                    if (d2 < nearest) {
                        nearest = d2;
                        nearestH = cell[2];
                    }
                }

                double dist = Math.sqrt(nearest);
                if (dist >= SEAMOUNT_RADIUS) {
                    data[lz][lx] = 0;
                } else {
                    double t = 1.0 - dist / SEAMOUNT_RADIUS;
                    data[lz][lx] = nearestH * (t * t * (3 - 2 * t));
                }
            }
        }
        return data;
    }

    private static class PerlinNoise {
        private final int[] perm;
        private final int octaves;
        private final double lacunarity;
        private final double gain;

        PerlinNoise(long seed, int maxOctaves, double lacunarity, double gain) {
            this.octaves = maxOctaves;
            this.lacunarity = lacunarity;
            this.gain = gain;
            Random rng = new Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
            }
            perm = new int[512];
            System.arraycopy(p, 0, perm, 0, 256);
            System.arraycopy(p, 0, perm, 256, 256);
        }

        double noise(double x, double y) {
            double value = 0;
            double amp = 1;
            double freq = 1;
            double maxAmp = 0;
            for (int i = 0; i < octaves; i++) {
                value += raw(x * freq, y * freq) * amp;
                maxAmp += amp;
                amp *= gain;
                freq *= lacunarity;
            }
            return value / maxAmp;
        }

        private double raw(double x, double y) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            double xf = x - Math.floor(x);
            double yf = y - Math.floor(y);
            double u = fade(xf);
            double v = fade(yf);
            int aa = perm[perm[xi] + yi];
            int ab = perm[perm[xi] + yi + 1];
            int ba = perm[perm[xi + 1] + yi];
            int bb = perm[perm[xi + 1] + yi + 1];
            double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
            double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
            return lerp(x1, x2, v);
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static double lerp(double a, double b, double t) {
            return a + t * (b - a);
        }

        private static double grad(int hash, double x, double y) {
            switch (hash & 3) {
                case 0: return x + y;
                case 1: return -x + y;
                case 2: return x - y;
                case 3: return -x - y;
                default: return 0;
            }
        }

        private static double fadeDerivative(double t) {
            return 30 * t * t * (1 - t) * (1 - t);
        }

        private double[] rawWithGradient(double x, double y) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            double xf = x - Math.floor(x);
            double yf = y - Math.floor(y);
            double u = fade(xf);
            double v = fade(yf);
            double du = fadeDerivative(xf);
            double dv = fadeDerivative(yf);

            int aa = perm[perm[xi] + yi];
            int ab = perm[perm[xi] + yi + 1];
            int ba = perm[perm[xi + 1] + yi];
            int bb = perm[perm[xi + 1] + yi + 1];

            int h00 = aa & 3, h10 = ba & 3, h01 = ab & 3, h11 = bb & 3;

            double gx00 = 1 - 2 * (h00 & 1), gy00 = 1 - (h00 & 2);
            double gx10 = 1 - 2 * (h10 & 1), gy10 = 1 - (h10 & 2);
            double gx01 = 1 - 2 * (h01 & 1), gy01 = 1 - (h01 & 2);
            double gx11 = 1 - 2 * (h11 & 1), gy11 = 1 - (h11 & 2);

            double n00 = gx00 * xf + gy00 * yf;
            double n10 = gx10 * (xf - 1) + gy10 * yf;
            double n01 = gx01 * xf + gy01 * (yf - 1);
            double n11 = gx11 * (xf - 1) + gy11 * (yf - 1);

            double A = n00 + u * (n10 - n00);
            double B = n01 + u * (n11 - n01);
            double value = A + v * (B - A);

            double dAx = gx00 + u * (gx10 - gx00) + du * (n10 - n00);
            double dBx = gx01 + u * (gx11 - gx01) + du * (n11 - n01);
            double dx = dAx + v * (dBx - dAx);

            double dAy = gy00 + u * (gy10 - gy00);
            double dBy = gy01 + u * (gy11 - gy01);
            double dy = dAy + v * (dBy - dAy) + dv * (B - A);

            return new double[]{value, dx, dy};
        }

        double fbmEroded(double x, double y, double erosionStrength) {
            double value = 0;
            double totalWeight = 0;
            double gx = 0, gy = 0;
            double amp = 1;
            double freq = 1;

            for (int i = 0; i < octaves; i++) {
                double steepness = Math.sqrt(gx * gx + gy * gy);
                double influence = 1.0 / (1.0 + steepness * erosionStrength);

                double[] ng = rawWithGradient(x * freq, y * freq);

                double weight = amp * influence;
                value += ng[0] * weight;
                totalWeight += weight;

                gx += ng[1] * freq * weight;
                gy += ng[2] * freq * weight;

                amp *= gain;
                freq *= lacunarity;
            }

            return totalWeight > 0 ? value / totalWeight : 0;
        }

        double fbmErodedWithZone(double x, double y) {
            double[] first = rawWithGradient(x, y);
            double firstVal = first[0];

            double erosionStrength;
            int nOctaves;
            if (firstVal < -SHELF_BOUNDARY) {
                erosionStrength = PLAINS_EROSION;
                nOctaves = 4;
            } else if (firstVal > SHELF_BOUNDARY) {
                erosionStrength = SHALLOWS_EROSION;
                nOctaves = 4;
            } else {
                erosionStrength = SHELF_EROSION;
                nOctaves = octaves;
            }

            double value = first[0];
            double totalWeight = 1.0;
            double gx = first[1];
            double gy = first[2];
            double amp = gain;
            double freq = lacunarity;

            for (int i = 1; i < nOctaves; i++) {
                double steepness = Math.sqrt(gx * gx + gy * gy);
                double influence = 1.0 / (1.0 + steepness * erosionStrength);

                double[] ng = rawWithGradient(x * freq, y * freq);

                double weight = amp * influence;
                value += ng[0] * weight;
                totalWeight += weight;

                gx += ng[1] * freq * weight;
                gy += ng[2] * freq * weight;

                amp *= gain;
                freq *= lacunarity;
            }

            return totalWeight > 0 ? value / totalWeight : 0;
        }
    }
}
