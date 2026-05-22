package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubLevelPhysicsSystem.class)
public class SubLevelPhysicsSystemMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(final SubLevelContainer sidelessContainer, final CallbackInfo ci) {
    }
}
