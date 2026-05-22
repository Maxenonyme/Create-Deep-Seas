package com.maxenonyme.createsubmarine.submarine.compat;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.stress.DefaultMaterialProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

public class CopycatsCompat {
    private static Boolean copycatsLoaded = null;
    private static Class<?> copycatBlockClass = null;
    private static Class<?> copycatBEClass = null;
    private static Method getMaterialMethod = null;

    public static boolean isLoaded() {
        if (copycatsLoaded == null) {
            try {
                copycatBlockClass = Class.forName("com.copycatsplus.copycats.block.ICopycatBlock");
                copycatBEClass = Class.forName("com.copycatsplus.copycats.block.entity.CopycatBlockEntity");
                copycatsLoaded = true;
                CreateSubmarine.LOGGER.info("Copycats+ detected, material inheritance enabled");
            } catch (ClassNotFoundException e) {
                copycatsLoaded = false;
            }
        }
        return copycatsLoaded;
    }

    public static boolean isCopycatBlock(BlockState state) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String ns = id.getNamespace();
        String path = id.getPath();
        return ns.equals("create") && path.startsWith("copycat")
            || ns.equals("copycats") && path.startsWith("copycat");
    }

    public static boolean isCreateCopycat(ResourceLocation id) {
        return id.getNamespace().equals("create") && id.getPath().startsWith("copycat");
    }

    public static boolean isCopycatsPlusBlock(ResourceLocation id) {
        return id.getNamespace().equals("copycats") && id.getPath().startsWith("copycat");
    }

    public static ResourceLocation getMimickedBlock(Level level, BlockPos pos) {
        if (!isLoaded()) return null;
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !copycatBEClass.isInstance(be)) return null;

            if (getMaterialMethod == null) {
                getMaterialMethod = copycatBEClass.getMethod("getMaterial");
            }
            Object material = getMaterialMethod.invoke(be);
            if (material == null) return null;

            Method getBlock = material.getClass().getMethod("getBlock");
            Object block = getBlock.invoke(material);
            if (block instanceof net.minecraft.world.level.block.Block b) {
                return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
            }
            Method getId = material.getClass().getMethod("getId");
            Object idObj = getId.invoke(material);
            if (idObj instanceof ResourceLocation rl) return rl;
        } catch (Exception e) {
            // Reflection failed, fall through
        }
        return null;
    }

    public static double[] getMaterialProperties(Level level, BlockPos pos, BlockState state) {
        ResourceLocation copycatId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());

        double[] defaultProps = DefaultMaterialProperties.getProperties(copycatId);
        if (defaultProps == null) {
            defaultProps = new double[]{2.00e11, 3.00e8, 0.28};
        }

        // When inheritance is enabled, try to get the mimicked block's properties
        if (net.neoforged.fml.ModList.get().isLoaded("copycats") && isCopycatBlock(state)) {
            ResourceLocation mimickedId = getMimickedBlock(level, pos);
            if (mimickedId != null) {
                double[] mimickedProps = DefaultMaterialProperties.getProperties(mimickedId);
                if (mimickedProps != null) {
                    return mimickedProps;
                }
            }
        }

        return defaultProps;
    }
}
