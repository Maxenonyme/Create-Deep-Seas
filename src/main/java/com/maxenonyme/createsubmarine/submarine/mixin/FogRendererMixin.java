package com.maxenonyme.createsubmarine.submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.maxenonyme.createsubmarine.submarine.client.SubmarineFogHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private static FogType createsubmarine$submergedPocketFog(Camera instance, Operation<FogType> original) {
        FogType type = original.call(instance);
        if (type == FogType.NONE && SubmarineFogHandler.shouldFog()) {
            return FogType.WATER;
        }
        return type;
    }
}
