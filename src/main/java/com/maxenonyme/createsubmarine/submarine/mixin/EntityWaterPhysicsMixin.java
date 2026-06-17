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

    @org.spongepowered.asm.mixin.Shadow
    protected boolean wasTouchingWater;
    @org.spongepowered.asm.mixin.Shadow
    protected it.unimi.dsi.fastutil.objects.Object2DoubleMap<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>> fluidHeight;
    @org.spongepowered.asm.mixin.Shadow(remap = false)
    @org.spongepowered.asm.mixin.Final
    private it.unimi.dsi.fastutil.objects.Object2DoubleMap<net.neoforged.neoforge.fluids.FluidType> forgeFluidTypeHeight;

    @Inject(method = "updateInWaterStateAndDoFluidPushing", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$cancelWaterPushing(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) {
            wasTouchingWater = false;
            fluidHeight.clear();
            forgeFluidTypeHeight.clear();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isEyeInFluid", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isEyeInFluid(net.minecraft.tags.TagKey<?> fluidTag, CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) cir.setReturnValue(false);
    }

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$noSplashWhileSubmerged(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        net.minecraft.core.BlockPos eye = net.minecraft.core.BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        net.minecraft.world.level.material.FluidState fs = entity.level().getFluidState(eye);
        if (fs.is(net.minecraft.tags.FluidTags.WATER)
                && entity.getEyeY() < eye.getY() + fs.getHeight(entity.level(), eye)) {
            ci.cancel();
        }
    }

    @Inject(method = "isUnderWater", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isUnderWater(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) cir.setReturnValue(false);
    }

    @Inject(method = "updateFluidOnEyes", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$updateFluidOnEyes(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (createsubmarine$isInsideAirtightSub()) {
            ci.cancel();
        }
    }

    @Inject(method = "getEyeInFluidType", at = @At("HEAD"), cancellable = true, remap = false)
    private void createsubmarine$getEyeInFluidType(CallbackInfoReturnable<net.neoforged.neoforge.fluids.FluidType> cir) {
        if (createsubmarine$isInsideAirtightSub()) {
            cir.setReturnValue(net.neoforged.neoforge.common.NeoForgeMod.EMPTY_TYPE.value());
        }
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$updateSwimming(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (createsubmarine$isInsideAirtightSub()) {
            Entity entity = (Entity) (Object) this;
            entity.setSwimming(false);
            ci.cancel();
        }
    }

    @Inject(method = "isSwimming", at = @At("HEAD"), cancellable = true)
    private void createsubmarine$isSwimming(CallbackInfoReturnable<Boolean> cir) {
        if (createsubmarine$isInsideAirtightSub()) cir.setReturnValue(false);
    }

    @Unique
    private long createsubmarine$cacheTick = -1L;
    @Unique
    private double createsubmarine$cacheX, createsubmarine$cacheY, createsubmarine$cacheZ, createsubmarine$cacheEyeY;
    @Unique
    private boolean createsubmarine$cachedInside = false;

    @Unique
    private boolean createsubmarine$isInsideAirtightSub() {
        Entity entity = (Entity) (Object) this;
        net.minecraft.world.level.Level level = entity.level();
        if (level == null) return false;

        long tick = level.getGameTime();
        double x = entity.getX(), y = entity.getY(), z = entity.getZ(), eyeY = entity.getEyeY();
        if (tick == createsubmarine$cacheTick
                && x == createsubmarine$cacheX && y == createsubmarine$cacheY
                && z == createsubmarine$cacheZ && eyeY == createsubmarine$cacheEyeY) {
            return createsubmarine$cachedInside;
        }

        net.minecraft.world.phys.Vec3 eyePos = new net.minecraft.world.phys.Vec3(x, eyeY, z);
        boolean inside = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.isInSealedExact(level, eyePos);
        if (!inside) {
            inside = com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker.isInSealedExact(level, entity.position());
        }

        if (inside) {
            dev.ryanhcode.sable.companion.SubLevelAccess sub = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(level, eyePos);
            if (sub != null) {
                org.joml.Vector3d localPos = new org.joml.Vector3d(x, eyeY, z);
                sub.logicalPose().transformPositionInverse(localPos);
                net.minecraft.core.BlockPos localBlockPos = net.minecraft.core.BlockPos.containing(localPos.x, localPos.y, localPos.z);
                
                net.minecraft.world.level.Level subLevel = com.maxenonyme.createsubmarine.submarine.util.SubLevelRegistry.getLevel(sub.getUniqueId());
                if (subLevel == null && sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl) {
                    subLevel = sl.getLevel();
                }
                
                if (subLevel != null) {
                    net.minecraft.world.level.material.FluidState fs = subLevel.getFluidState(localBlockPos);
                    if (fs.is(net.minecraft.tags.FluidTags.WATER)) {
                        inside = false;
                    } else {
                        org.joml.Vector3d localFeet = new org.joml.Vector3d(x, y, z);
                        sub.logicalPose().transformPositionInverse(localFeet);
                        net.minecraft.core.BlockPos localFeetPos = net.minecraft.core.BlockPos.containing(localFeet.x, localFeet.y, localFeet.z);
                        if (subLevel.getFluidState(localFeetPos).is(net.minecraft.tags.FluidTags.WATER)) {
                            inside = false;
                        }
                    }
                }
            }
        }

        createsubmarine$cacheTick = tick;
        createsubmarine$cacheX = x;
        createsubmarine$cacheY = y;
        createsubmarine$cacheZ = z;
        createsubmarine$cacheEyeY = eyeY;
        createsubmarine$cachedInside = inside;
        return inside;
    }

}
