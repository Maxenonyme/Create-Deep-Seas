package com.maxenonyme.createsubmarine.submarine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads Sponge schematic v2 (.schem) files and provides them as a BlockGetter
 * for use with LatticeStressSolver's test battery.
 *
 * Format: NBT compound with:
 *   - Version (int) = 2
 *   - DataVersion (int)
 *   - Width, Height, Length (short)
 *   - Palette (Compound: block-state-string -> int)
 *   - BlockData (byte array, XZY order: index = (y*Length + z)*Width + x)
 */
public class SchematicLoader {

    public record SchematicData(
        int width, int height, int length,
        BlockState[] palette,
        byte[] blockData,
        int[] offset
    ) {
        public int volume() { return width * height * length; }
    }

    public static SchematicData load(File file) throws IOException {
        CompoundTag root;
        try (FileInputStream fis = new FileInputStream(file)) {
            root = NbtIo.readCompressed(fis, net.minecraft.nbt.NbtAccounter.create(Long.MAX_VALUE));
        }

        int version = root.getInt("Version");
        if (version != 2) {
            throw new IOException("Unsupported schematic version: " + version + " (expected 2)");
        }

        int width = root.getShort("Width") & 0xFFFF;
        int height = root.getShort("Height") & 0xFFFF;
        int length = root.getShort("Length") & 0xFFFF;

        int[] offset = new int[3];
        if (root.contains("OffsetX", 99)) {
            offset[0] = root.getInt("OffsetX");
            offset[1] = root.getInt("OffsetY");
            offset[2] = root.getInt("OffsetZ");
        }

        CompoundTag paletteTag = root.getCompound("Palette");
        int paletteMax = root.getInt("PaletteMax");

        BlockState[] palette = new BlockState[paletteMax];
        for (String key : paletteTag.getAllKeys()) {
            int idx = paletteTag.getInt(key);
            palette[idx] = parseBlockState(key);
        }
        for (int i = 0; i < paletteMax; i++) {
            if (palette[i] == null) palette[i] = Blocks.AIR.defaultBlockState();
        }

        byte[] blockData = root.getByteArray("BlockData");
        if (blockData.length != width * height * length) {
            byte[] resized = new byte[width * height * length];
            System.arraycopy(blockData, 0, resized, 0, Math.min(blockData.length, resized.length));
            blockData = resized;
        }

        return new SchematicData(width, height, length, palette, blockData, offset);
    }

    public static BlockGetter toBlockGetter(SchematicData data) {
        return new SchematicBlockGetter(data);
    }

    public static BlockGetter loadBlockGetter(File file) throws IOException {
        return toBlockGetter(load(file));
    }

    private static BlockState parseBlockState(String key) {
        try {
            String[] parts = key.split("\\[", 2);
            String blockId = parts[0];
            ResourceLocation rl = ResourceLocation.parse(blockId);
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        } catch (Exception e) {
            return Blocks.STONE.defaultBlockState();
        }
    }

    private static class SchematicBlockGetter implements BlockGetter {
        private final SchematicData data;
        private final Map<BlockPos, BlockState> cache = new HashMap<>();

        SchematicBlockGetter(SchematicData data) {
            this.data = data;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return cache.computeIfAbsent(pos.immutable(), this::resolveBlock);
        }

        private BlockState resolveBlock(BlockPos pos) {
            int lx = pos.getX() - data.offset[0];
            int ly = pos.getY() - data.offset[1];
            int lz = pos.getZ() - data.offset[2];
            if (lx < 0 || lx >= data.width ||
                ly < 0 || ly >= data.height ||
                lz < 0 || lz >= data.length) {
                return Blocks.AIR.defaultBlockState();
            }
            int index = (ly * data.length + lz) * data.width + lx;
            if (index < 0 || index >= data.blockData.length) {
                return Blocks.AIR.defaultBlockState();
            }
            int paletteIdx = data.blockData[index] & 0xFF;
            if (paletteIdx < 0 || paletteIdx >= data.palette.length) {
                return Blocks.AIR.defaultBlockState();
            }
            return data.palette[paletteIdx];
        }

        @Nullable @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
            return Optional.empty();
        }

        @Override
        public int getHeight() { return data.offset[1] + data.height; }

        @Override
        public int getMinBuildHeight() { return data.offset[1]; }

        @Override
        public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
    }
}
