package com.maxenonyme.createsubmarine.submarine.mixin;

import com.maxenonyme.createsubmarine.submarine.util.ILargeWaterWheel;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WaterWheelBlockEntity.class, remap = false)
public abstract class WaterWheelBlockEntityMixin {

    @Inject(method = "getGeneratedSpeed", at = @At("RETURN"), cancellable = true)
    private void createsubmarine$getGeneratedSpeed(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LargeWaterWheelBlockEntity) {
            if (this instanceof ILargeWaterWheel ext) {
                float shipSpeed = ext.createsubmarine$getShipSpeedOffset();
                if (Math.abs(shipSpeed) > Math.abs(cir.getReturnValue())) {
                    cir.setReturnValue(shipSpeed);
                }
            }
        }
    }
}
