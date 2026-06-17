package com.maxenonyme.createsubmarine.submarine.mixin;

import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.maxenonyme.createsubmarine.submarine.block.PulleyBlock;

@Mixin(value = RapierVoxelColliderBakery.class, remap = false)
public class RapierVoxelColliderBakeryMixin {

    @Inject(method = "getPhysicsDataForBlock", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$getPhysicsDataForBlock(BlockState state, CallbackInfoReturnable<RapierVoxelColliderData> cir) {
        if (state.getBlock() instanceof PulleyBlock) {
            cir.setReturnValue(RapierVoxelColliderData.EMPTY);
        }
    }
}
