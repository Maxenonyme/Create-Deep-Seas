package com.maxenonyme.createsubmarine.submarine.mixin;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CreateSubmarineMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("CreateSubmarineMixinPlugin loaded! Package: {}", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        LOGGER.info("shouldApplyMixin: target={}, mixin={}", targetClassName, mixinClassName);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
