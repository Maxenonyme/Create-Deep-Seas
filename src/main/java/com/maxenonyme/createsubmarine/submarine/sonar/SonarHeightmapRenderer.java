package com.maxenonyme.createsubmarine.submarine.sonar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import com.maxenonyme.createsubmarine.submarine.network.SonarScanPayload;
import org.joml.*;

public class SonarHeightmapRenderer {

    public static void renderHeightmap(AdvancedFbo fbo, SonarScanPayload scan,
                                        Matrix4f projMatrix, Matrix4f viewMatrix) {
        fbo.bind(true);
        fbo.clear();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(viewMatrix);
        RenderSystem.applyModelViewMatrix();

        Matrix4f backupProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getProjectionMatrix().set(projMatrix);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int gs = scan.gridSize();
        float cellSize = scan.step();
        int half = gs / 2;

        for (int gx = 0; gx < gs - 1; gx++) {
            for (int gz = 0; gz < gs - 1; gz++) {
                int idx00 = gx * gs + gz;
                int idx10 = (gx + 1) * gs + gz;
                int idx01 = gx * gs + gz + 1;
                int idx11 = (gx + 1) * gs + gz + 1;

                byte h00 = scan.heights()[idx00];
                byte h10 = scan.heights()[idx10];
                byte h01 = scan.heights()[idx01];
                byte h11 = scan.heights()[idx11];

                if (h00 == -128 && h10 == -128 && h01 == -128 && h11 == -128) continue;

                float y00 = h00 == -128 ? -999 : h00;
                float y10 = h10 == -128 ? -999 : h10;
                float y01 = h01 == -128 ? -999 : h01;
                float y11 = h11 == -128 ? -999 : h11;

                float wx = (gx - half) * cellSize;
                float wz = (gz - half) * cellSize;

                int c00 = h00 == -128 ? 0 : scan.colors()[idx00];
                int c10 = h10 == -128 ? 0 : scan.colors()[idx10];
                int c01 = h01 == -128 ? 0 : scan.colors()[idx01];
                int c11 = h11 == -128 ? 0 : scan.colors()[idx11];

                float r00 = ((c00 >> 16) & 0xFF) / 255f;
                float g00 = ((c00 >> 8) & 0xFF) / 255f;
                float b00 = (c00 & 0xFF) / 255f;
                float r10 = ((c10 >> 16) & 0xFF) / 255f;
                float g10 = ((c10 >> 8) & 0xFF) / 255f;
                float b10 = (c10 & 0xFF) / 255f;
                float r01 = ((c01 >> 16) & 0xFF) / 255f;
                float g01 = ((c01 >> 8) & 0xFF) / 255f;
                float b01 = (c01 & 0xFF) / 255f;
                float r11 = ((c11 >> 16) & 0xFF) / 255f;
                float g11 = ((c11 >> 8) & 0xFF) / 255f;
                float b11 = (c11 & 0xFF) / 255f;

                float a00 = h00 == -128 ? 0 : 1;
                float a10 = h10 == -128 ? 0 : 1;
                float a01 = h01 == -128 ? 0 : 1;
                float a11 = h11 == -128 ? 0 : 1;

                buffer.addVertex(wx, y00, wz).setColor(r00, g00, b00, a00);
                buffer.addVertex(wx + cellSize, y10, wz).setColor(r10, g10, b10, a10);
                buffer.addVertex(wx + cellSize, y11, wz + cellSize).setColor(r11, g11, b11, a11);
                buffer.addVertex(wx, y01, wz + cellSize).setColor(r01, g01, b01, a01);

                float bottom = -128f;
                float rBtm = 0.05f, gBtm = 0.1f, bBtm = 0.05f;

                buffer.addVertex(wx, bottom, wz).setColor(rBtm, gBtm, bBtm, 1);
                buffer.addVertex(wx, y00, wz).setColor(r00, g00, b00, a00);
                buffer.addVertex(wx, y01, wz + cellSize).setColor(r01, g01, b01, a01);
                buffer.addVertex(wx, bottom, wz + cellSize).setColor(rBtm, gBtm, bBtm, 1);

                buffer.addVertex(wx + cellSize, bottom, wz).setColor(rBtm, gBtm, bBtm, 1);
                buffer.addVertex(wx + cellSize, y10, wz).setColor(r10, g10, b10, a10);
                buffer.addVertex(wx + cellSize, y11, wz + cellSize).setColor(r11, g11, b11, a11);
                buffer.addVertex(wx + cellSize, bottom, wz + cellSize).setColor(rBtm, gBtm, bBtm, 1);

                buffer.addVertex(wx, bottom, wz).setColor(rBtm, gBtm, bBtm, 1);
                buffer.addVertex(wx + cellSize, bottom, wz).setColor(rBtm, gBtm, bBtm, 1);
                buffer.addVertex(wx + cellSize, bottom, wz + cellSize).setColor(rBtm, gBtm, bBtm, 1);
                buffer.addVertex(wx, bottom, wz + cellSize).setColor(rBtm, gBtm, bBtm, 1);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.getProjectionMatrix().set(backupProj);

        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        AdvancedFbo.unbind();
    }

    public static void renderSubMarker(AdvancedFbo fbo, AABB subBox, int originX, int originY, int originZ,
                                        Matrix4f projMatrix, Matrix4f viewMatrix, float time) {
        fbo.bind(true);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        modelViewStack.mul(viewMatrix);
        RenderSystem.applyModelViewMatrix();

        Matrix4f backupProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.getProjectionMatrix().set(projMatrix);

        float cx = (float) ((subBox.minX + subBox.maxX) / 2) - originX;
        float cy = (float) ((subBox.minY + subBox.maxY) / 2) - originY;
        float cz = (float) ((subBox.minZ + subBox.maxZ) / 2) - originZ;

        float pulse = 0.5f + 0.5f * (float) java.lang.Math.sin(time * 3.0);
        float r = 0.2f + 0.8f * (1 - pulse);
        float g = 1.0f;
        float b = 0.2f + 0.8f * pulse;

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float s = 2.0f;
        buffer.addVertex(cx - s, cy, cz - s).setColor(r, g, b, 1);
        buffer.addVertex(cx + s, cy, cz - s).setColor(r, g, b, 1);
        buffer.addVertex(cx + s, cy, cz + s).setColor(r, g, b, 1);
        buffer.addVertex(cx - s, cy, cz + s).setColor(r, g, b, 1);

        buffer.addVertex(cx - s, cy - s * 0.5f, cz).setColor(r, g, b, 0.3f);
        buffer.addVertex(cx + s, cy - s * 0.5f, cz).setColor(r, g, b, 0.3f);
        buffer.addVertex(cx + s, cy + 8, cz).setColor(r, g, b, 0);
        buffer.addVertex(cx - s, cy + 8, cz).setColor(r, g, b, 0);

        buffer.addVertex(cx, cy - s * 0.5f, cz - s).setColor(r, g, b, 0.3f);
        buffer.addVertex(cx, cy - s * 0.5f, cz + s).setColor(r, g, b, 0.3f);
        buffer.addVertex(cx, cy + 8, cz + s).setColor(r, g, b, 0);
        buffer.addVertex(cx, cy + 8, cz - s).setColor(r, g, b, 0);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.getProjectionMatrix().set(backupProj);
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        AdvancedFbo.unbind();
    }
}
