package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.math.RotatedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGetBoundingBoxMixin {

    @Shadow
    private AABB bb;

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$getBoundingBox(CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof RotatedEntity rotated) {
            cir.setReturnValue(rotated.getRotatedHitbox().getWorldAABB());
        }
    }
}
