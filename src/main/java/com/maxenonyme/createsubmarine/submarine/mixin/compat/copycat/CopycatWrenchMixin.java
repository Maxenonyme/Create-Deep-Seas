package com.maxenonyme.createsubmarine.submarine.mixin.compat.copycat;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.copycatsplus.copycats.foundation.copycat.ICopycatBlock", remap = false)
public interface CopycatWrenchMixin {

    @Inject(method = "onWrenched", at = @At("HEAD"), cancellable = true)
    default void createsubmarine$interceptWrench(BlockState state, UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        net.minecraft.world.level.Level level = context.getLevel();
        net.minecraft.core.BlockPos clickedPos = context.getClickedPos();
        
        java.util.UUID subId = null;
        net.minecraft.core.BlockPos plotPos = clickedPos;
        dev.ryanhcode.sable.companion.SubLevelAccess sub = null;

        if (level instanceof dev.ryanhcode.sable.companion.SubLevelAccess sla) {
            subId = sla.getUniqueId();
            sub = sla;
        } else {
            sub = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level, clickedPos);
            if (sub != null) {
                subId = sub.getUniqueId();
                org.joml.Vector3d local = new org.joml.Vector3d(clickedPos.getX() + 0.5, clickedPos.getY() + 0.5, clickedPos.getZ() + 0.5);
                dev.ryanhcode.sable.companion.math.Pose3dc pose = sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel csl ? csl.renderPose() : sub.logicalPose();
                pose.transformPositionInverse(local);
                plotPos = net.minecraft.core.BlockPos.containing(local.x, local.y, local.z);
            }
        }

        if (subId != null && sub != null) {
            boolean sealed = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.hasAnySealed(subId);
            boolean pressurized = false;
            boolean hasCrack = false;

            if (level.isClientSide()) {
                pressurized = sub.logicalPose().position().y() < 62;
                hasCrack = com.maxenonyme.createsubmarine.submarine.util.CrackUtil.hasCrack(subId, plotPos);
            } else {
                pressurized = com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem.isPressurized(subId);
                java.util.Map<net.minecraft.core.BlockPos, Integer> cracks = com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem.getAllCracks().get(subId);
                hasCrack = cracks != null && cracks.containsKey(plotPos);
            }

            if (hasCrack || (sealed && pressurized)) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }
}
