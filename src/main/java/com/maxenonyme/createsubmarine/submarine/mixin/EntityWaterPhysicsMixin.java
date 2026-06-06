package com.maxenonyme.createsubmarine.submarine.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public abstract class EntityWaterPhysicsMixin {

    @Inject(method = "isInWater", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isInWater(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) cir.setReturnValue(false);
    }

    @Inject(method = "getFluidHeight(Lnet/minecraft/tags/TagKey;)D", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$getFluidHeight(net.minecraft.tags.TagKey<?> fluidTag, CallbackInfoReturnable<Double> cir) {
        if (createsubmarine$isInsideAirtightSub()) cir.setReturnValue(0.0D);
    }

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$cancelWaterPushing(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) cir.setReturnValue(false);
    }

    @Unique
    private boolean createsubmarine$isInsideAirtightSub() {
        Entity entity = (Entity) (Object) this;
        if (entity.level() == null) return false;
        return com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.isInSealed(entity.level(), entity.blockPosition());
    }
}
