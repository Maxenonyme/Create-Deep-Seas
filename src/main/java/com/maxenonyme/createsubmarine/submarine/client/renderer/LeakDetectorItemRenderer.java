package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class LeakDetectorItemRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext context, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {
        float worldTime = AnimationTickHolder.getRenderTime() / 20;

        float scale;
        switch (context) {
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> scale = 0.8F;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> scale = 0.5F;
            case GUI -> scale = 0.6F;
            case GROUND -> scale = 0.25F;
            default -> scale = 0.5F;
        }

        ms.scale(scale, scale, scale);

        renderer.render(AllPartialModels.LEAK_DETECTOR_CORE.get(), light);

        float pivotY = 25.8F / 16F - 0.5F;

        ms.pushPose();
        ms.translate(-0.5F, pivotY, -0.5F);
        ms.mulPose(Axis.YP.rotationDegrees(worldTime * 15));
        ms.translate(0.5F, -pivotY, 0.5F);
        renderer.render(AllPartialModels.LEAK_DETECTOR_CASING.get(), light);
        ms.popPose();

        ms.pushPose();
        ms.translate(-0.5F, pivotY, -0.5F);
        ms.mulPose(Axis.YP.rotationDegrees(worldTime * 30));
        float pulse = 1.0F + (float) (java.lang.Math.sin(worldTime * 3.0) * 0.1);
        ms.scale(pulse, pulse, pulse);
        ms.translate(0.5F / pulse, -pivotY / pulse, 0.5F / pulse);
        renderer.render(AllPartialModels.LEAK_DETECTOR_EYE.get(), LightTexture.FULL_BRIGHT);
        ms.popPose();
    }
}
