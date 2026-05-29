package com.maxenonyme.AbyssDimension.mixin;

import com.maxenonyme.AbyssDimension.client.PDAManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @Unique
    private final int[] createsubmarine$darkRowCache = new int[16];

    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void createsubmarine$redirectSetPixelRGBA(NativeImage instance, int x, int y, int color) {
        if (PDAManager.isFlickering() && !PDAManager.isLightsOn()) {
            if (x == 0) {
                createsubmarine$darkRowCache[y] = color;
            }
            color = createsubmarine$darkRowCache[y];
        }
        instance.setPixelRGBA(x, y, color);
    }
}
