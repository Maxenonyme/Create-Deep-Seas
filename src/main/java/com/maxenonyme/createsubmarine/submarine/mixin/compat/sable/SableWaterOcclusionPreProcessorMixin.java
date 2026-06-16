package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.maxenonyme.createsubmarine.submarine.util.CompatUtil;
import dev.ryanhcode.sable.render.water_occlusion.SableWaterOcclusionPreProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = SableWaterOcclusionPreProcessor.class, remap = false)
public abstract class SableWaterOcclusionPreProcessorMixin {

    @Inject(method = "modify", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsubmarine$skipVanillaWaterDiscard(@Coerce Object ctx, @Coerce Object tree, CallbackInfo ci) {
        if (CompatUtil.isSodiumLoaded()) {
            ci.cancel();
        }
    }
}
