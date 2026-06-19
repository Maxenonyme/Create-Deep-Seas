package com.maxenonyme.createsubmarine.submarine.item.purge_staff;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PurgeStaffItemRenderer extends CustomRenderedItemModelRenderer {

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

        renderer.render(com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels.PURGE_STAFF.get(),
                light);

        float pivotY = 20.0F / 16F - 0.5F;

        ms.pushPose();
        ms.translate(-0.5F, pivotY, -0.5F);
        ms.mulPose(Axis.YP.rotationDegrees(worldTime * 15));
        ms.translate(0.5F, -pivotY, 0.5F);
        renderer.render(com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels.PURGE_STAFF_CASING.get(),
                Sheets.translucentCullBlockSheet(), LightTexture.FULL_BRIGHT);
        ms.popPose();

        ms.pushPose();
        ms.translate(-0.5F, pivotY, -0.5F);
        ms.mulPose(Axis.YP.rotationDegrees(-worldTime * 20));
        float pulse = 1.0F + (float) (java.lang.Math.sin(worldTime * 3.0) * 0.1);
        ms.scale(pulse, pulse, pulse);
        ms.translate(0.5F / pulse, -pivotY / pulse, 0.5F / pulse);
        renderer.render(com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels.PURGE_STAFF_EYE.get(),
                Sheets.translucentCullBlockSheet(), LightTexture.FULL_BRIGHT);
        ms.popPose();
    }
}
