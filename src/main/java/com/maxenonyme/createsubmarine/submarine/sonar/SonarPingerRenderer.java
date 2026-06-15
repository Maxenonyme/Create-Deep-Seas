package com.maxenonyme.createsubmarine.submarine.sonar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class SonarPingerRenderer extends EntityRenderer<SonarPingerEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/coal_block.png");

    public SonarPingerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SonarPingerEntity entity, float yaw, float pt, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        // Framework applies Axis.YP.rotationDegrees(180 - yRot) before calling render
        ms.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));

        VertexConsumer consumer = buffer.getBuffer(RenderType.solid());
        Matrix4f mat = ms.last().pose();

        float s = 0.25f;
        float d = 0.03125f;

        consumer.addVertex(mat, -s, -s, d).setColor(0.3f, 0.6f, 0.3f, 1).setUv(0, 0).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(mat, s, -s, d).setColor(0.3f, 0.6f, 0.3f, 1).setUv(1, 0).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(mat, s, s, d).setColor(0.3f, 0.6f, 0.3f, 1).setUv(1, 1).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(mat, -s, s, d).setColor(0.3f, 0.6f, 0.3f, 1).setUv(0, 1).setLight(light).setNormal(0, 0, 1);

        consumer.addVertex(mat, -s, -s, -d).setColor(0.3f, 0.3f, 0.3f, 1).setUv(0, 0).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(mat, s, -s, -d).setColor(0.3f, 0.3f, 0.3f, 1).setUv(1, 0).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(mat, s, s, -d).setColor(0.3f, 0.3f, 0.3f, 1).setUv(1, 1).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(mat, -s, s, -d).setColor(0.3f, 0.3f, 0.3f, 1).setUv(0, 1).setLight(light).setNormal(0, 0, -1);

        ms.popPose();
        super.render(entity, yaw, pt, ms, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SonarPingerEntity entity) {
        return TEXTURE;
    }
}
