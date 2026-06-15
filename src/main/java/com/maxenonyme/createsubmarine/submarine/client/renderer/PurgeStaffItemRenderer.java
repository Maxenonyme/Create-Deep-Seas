package com.maxenonyme.createsubmarine.submarine.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
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

        renderer.render(AllPartialModels.PURGE_STAFF_CORE.get(), Sheets.cutoutBlockSheet(), light);

        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(worldTime * 15));
        renderer.render(AllPartialModels.PURGE_STAFF_CASING.get(), Sheets.cutoutBlockSheet(), light);
        ms.popPose();

        ms.pushPose();
        float eyeY = 1.1125F;
        ms.translate(0, eyeY, 0);
        ms.mulPose(Axis.YP.rotationDegrees(worldTime * 30));
        float pulse = 1.0F + (float) (java.lang.Math.sin(worldTime * 3.0) * 0.1);
        ms.scale(pulse, pulse, pulse);
        ms.translate(0, -eyeY, 0);
        renderer.render(AllPartialModels.PURGE_STAFF_EYE.get(), Sheets.cutoutBlockSheet(), LightTexture.FULL_BRIGHT);
        ms.popPose();
    }
}
