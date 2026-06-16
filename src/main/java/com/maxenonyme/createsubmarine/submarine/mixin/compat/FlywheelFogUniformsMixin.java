package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionContainer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.backend.engine.uniform.FogUniforms", remap = false)
public class FlywheelFogUniformsMixin {

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getShaderFogStart()F"), require = 0)
    private static float createsubmarine$defogStart() {
        return createsubmarine$cameraInPocket() ? 1.0E30f : RenderSystem.getShaderFogStart();
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getShaderFogEnd()F"), require = 0)
    private static float createsubmarine$defogEnd() {
        return createsubmarine$cameraInPocket() ? 2.0E30f : RenderSystem.getShaderFogEnd();
    }

    @Unique
    private static boolean createsubmarine$cameraInPocket() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;
        WaterOcclusionContainer<?> container = WaterOcclusionContainer.getContainer(mc.level);
        if (container == null)
            return false;
        return container.isOccluded(mc.gameRenderer.getMainCamera().getPosition());
    }
}
