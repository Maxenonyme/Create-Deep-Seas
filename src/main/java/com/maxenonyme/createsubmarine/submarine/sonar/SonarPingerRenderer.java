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

    public SonarPingerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SonarPingerEntity entity, float yaw, float pt, PoseStack ms, MultiBufferSource buffer, int light) {
        ms.pushPose();
        ms.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f mat = ms.last().pose();

        float r = 0.25f;
        float d = 0.03125f;
        int segments = 16;

        // Disc top (sonar transducer) - green pulsing
        float pulse = 0.5f + 0.5f * (float) Math.sin(entity.tickCount * 0.1);
        float rCol = 0.15f + 0.1f * pulse;
        float gCol = 0.5f + 0.4f * pulse;
        float bCol = 0.15f;

        for (int i = 0; i < segments; i++) {
            double a1 = 2 * Math.PI * i / segments;
            double a2 = 2 * Math.PI * (i + 1) / segments;
            float x1 = (float) Math.cos(a1) * r;
            float z1 = (float) Math.sin(a1) * r;
            float x2 = (float) Math.cos(a2) * r;
            float z2 = (float) Math.sin(a2) * r;

            consumer.addVertex(mat, 0, 0, d).setColor(rCol, gCol, bCol, 1).setLight(light).setNormal(0, 0, 1);
            consumer.addVertex(mat, x1, z1, d).setColor(rCol, gCol, bCol, 1).setLight(light).setNormal(0, 0, 1);
            consumer.addVertex(mat, x2, z2, d).setColor(rCol * 0.7f, gCol * 0.7f, bCol * 0.7f, 1).setLight(light).setNormal(0, 0, 1);
        }

        // Direction indicator line
        float lineLen = 0.2f;
        consumer.addVertex(mat, 0, 0, d).setColor(0, 1, 0, 1).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(mat, 0, lineLen, d).setColor(0, 1, 0, 1).setLight(light).setNormal(0, 0, 1);

        // Ring around the edge
        for (int i = 0; i < segments; i++) {
            double a1 = 2 * Math.PI * i / segments;
            double a2 = 2 * Math.PI * (i + 1) / segments;
            float x1 = (float) Math.cos(a1) * r;
            float z1 = (float) Math.sin(a1) * r;
            float x2 = (float) Math.cos(a2) * r;
            float z2 = (float) Math.sin(a2) * r;

            consumer.addVertex(mat, x1, z1, d).setColor(0.1f, 0.4f, 0.1f, 1).setLight(light).setNormal(0, 0, 1);
            consumer.addVertex(mat, x2, z2, d).setColor(0.1f, 0.4f, 0.1f, 1).setLight(light).setNormal(0, 0, 1);
            consumer.addVertex(mat, x2, z2, -d).setColor(0.05f, 0.2f, 0.05f, 1).setLight(light).setNormal(0, 0, -1);
            consumer.addVertex(mat, x1, z1, -d).setColor(0.05f, 0.2f, 0.05f, 1).setLight(light).setNormal(0, 0, -1);
        }

        ms.popPose();
        super.render(entity, yaw, pt, ms, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SonarPingerEntity entity) {
        return null;
    }
}
