package com.maxenonyme.createsubmarine.submarine.mixin.compat.sable;

import com.maxenonyme.createsubmarine.submarine.system.MineOwnershipRegistry;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSubLevel.class)
public class ServerSubLevelMixin {

    @Inject(method = "setSplitFrom", at = @At("HEAD"))
    private void createsubmarine$inheritMineOwnership(ServerSubLevel containingSubLevel, Pose3d originalPose, CallbackInfo ci) {
        MineOwnershipRegistry.onSplit(((ServerSubLevel) (Object) this).getUniqueId(), containingSubLevel.getUniqueId());
    }
}
