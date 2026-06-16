package com.maxenonyme.createsubmarine.submarine.sonar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.network.SonarConfigPayload;
import com.maxenonyme.createsubmarine.submarine.network.SonarScanPayload;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.*;

import javax.annotation.Nullable;

import static org.lwjgl.opengl.GL11.*;

public class SonarScreen extends AbstractSimiScreen {

    private static final int VIEWPORT_SIZE = 256;
    private static final float FPS = 10.0f;

    private static final Vector3d LOCAL_CAM_POS = new Vector3d();
    private static final Matrix4f PROJECTION_MAT = new Matrix4f();
    private static final Quaternionf ORIENTATION = new Quaternionf();

    private final SonarPingerEntity sonarEntity;
    @Nullable
    private final ClientSubLevel subLevel;
    private final boolean isSubLevelMode;
    private SonarScanPayload currentScan;
    @Nullable
    private AABB subBoundingBox;
    private int subLevelMinY, subLevelMaxY;

    private AdvancedFbo fbo;
    private AdvancedFbo finalFbo;
    private float renderTime = FPS;
    private float totalTime = 0;

    private float yaw;
    private float pitch;

    public SonarScreen(SonarPingerEntity sonarEntity, @Nullable ClientSubLevel subLevel) {
        this.sonarEntity = sonarEntity;
        this.subLevel = subLevel;
        this.isSubLevelMode = subLevel != null;
        this.yaw = sonarEntity.getSonarYaw();
        this.pitch = sonarEntity.getSonarPitch();
        if (this.isSubLevelMode) {
            updateBoundingBox();
        }
    }

    public static void open(SonarPingerEntity sonar, @Nullable SubLevel subLevel) {
        ClientSubLevel clientSub = subLevel instanceof ClientSubLevel cs ? cs : null;
        Minecraft.getInstance().setScreen(new SonarScreen(sonar, clientSub));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));
    }

    private void updateBoundingBox() {
        if (this.subLevel == null || this.subLevel.isRemoved()) return;
        LevelPlot plot = this.subLevel.getPlot();
        BoundingBox3ic bounds = plot.getBoundingBox();
        Pose3dc pose = this.subLevel.logicalPose();
        Vector3d pos = new Vector3d(pose.position());
        this.subBoundingBox = new AABB(
                pos.x() + bounds.minX(), pos.y() + bounds.minY(), pos.z() + bounds.minZ(),
                pos.x() + bounds.maxX() + 1, pos.y() + bounds.maxY() + 1, pos.z() + bounds.maxZ() + 1
        );
        this.subLevelMinY = bounds.minY();
        this.subLevelMaxY = bounds.maxY();
    }

    public void updateScan(SonarScanPayload scan) {
        this.currentScan = scan;
        this.renderTime = Float.MAX_VALUE;
    }

    @Override
    public void onClose() {
        super.onClose();
        freeFramebuffers();
    }

    private void freeFramebuffers() {
        if (this.fbo != null) {
            this.fbo.free();
            this.fbo = null;
        }
        if (this.finalFbo != null) {
            this.finalFbo.free();
            this.finalFbo = null;
        }
    }

    @Override
    protected void init() {
        super.init();
        freeFramebuffers();
        this.fbo = AdvancedFbo.withSize(VIEWPORT_SIZE, VIEWPORT_SIZE).addColorTextureBuffer().setDepthTextureBuffer().build(true);
        this.finalFbo = AdvancedFbo.withSize(VIEWPORT_SIZE, VIEWPORT_SIZE).addColorTextureBuffer().build(true);

        updateOrientation();

        int vpX = this.width / 2 - VIEWPORT_SIZE / 2;
        int vpY = this.height / 2 - VIEWPORT_SIZE / 2;

        addRenderableWidget(new SonarButton(SonarButton.Arrow.UP,
                vpX + VIEWPORT_SIZE + 8, vpY + 4, 16, 16, () -> {
            this.pitch = Mth.clamp(this.pitch - 15, -90, 90);
            updateOrientation();
            sendConfigToServer();
        }));

        addRenderableWidget(new SonarButton(SonarButton.Arrow.DOWN,
                vpX + VIEWPORT_SIZE + 8, vpY + 22, 16, 16, () -> {
            this.pitch = Mth.clamp(this.pitch + 15, -90, 90);
            updateOrientation();
            sendConfigToServer();
        }));

        addRenderableWidget(new SonarButton(SonarButton.Arrow.LEFT,
                vpX + VIEWPORT_SIZE - 20, vpY + VIEWPORT_SIZE / 2 - 8, 16, 16, () -> {
            this.yaw = (this.yaw - 15) % 360;
            updateOrientation();
            sendConfigToServer();
        }));

        addRenderableWidget(new SonarButton(SonarButton.Arrow.RIGHT,
                vpX + VIEWPORT_SIZE + 4, vpY + VIEWPORT_SIZE / 2 - 8, 16, 16, () -> {
            this.yaw = (this.yaw + 15) % 360;
            updateOrientation();
            sendConfigToServer();
        }));
    }

    private void sendConfigToServer() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new SonarConfigPayload(this.sonarEntity.getId(), this.yaw, this.pitch));
    }

    private void updateOrientation() {
        this.renderTime = Float.MAX_VALUE;
        ORIENTATION.identity().rotateY((float) java.lang.Math.toRadians(this.yaw)).rotateX((float) java.lang.Math.toRadians(this.pitch));
    }

    private void renderScan(float partialTicks) {
        if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) return;
        if (isSubLevelMode && (this.subLevel == null || this.subLevel.isRemoved())) return;

        Minecraft minecraft = Minecraft.getInstance();

        if (this.renderTime >= 20.0f / FPS) {
            this.renderTime = 0;
        } else {
            this.renderTime += minecraft.getTimer().getRealtimeDeltaTicks();
            return;
        }

        if (this.fbo == null) return;

        if (isSubLevelMode) {
            updateBoundingBox();
        }

        float zNear = 0.1f;
        float radius = this.currentScan != null ? this.currentScan.radius() + 1 : 48;
        radius = java.lang.Math.max(radius, 2.0f);

        float aspect = 1.0f;
        PROJECTION_MAT.identity().ortho(-radius * aspect, radius * aspect, -radius, radius, zNear, radius * 2.0f);

        Vector3d center = new Vector3d(0, 0, 0);
        LOCAL_CAM_POS.set(center).add(ORIENTATION.transform(new Vector3d(0, 0, radius)));

        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.set(new Quaternionf(ORIENTATION).conjugate());
        viewMatrix.translate((float) -LOCAL_CAM_POS.x(), (float) -LOCAL_CAM_POS.y(), (float) -LOCAL_CAM_POS.z());

        if (this.currentScan != null) {
            SonarHeightmapRenderer.renderHeightmap(this.fbo, this.currentScan,
                    PROJECTION_MAT, viewMatrix);
            if (isSubLevelMode && this.subBoundingBox != null) {
                SonarHeightmapRenderer.renderSubMarker(this.fbo, this.subBoundingBox,
                        this.currentScan.centerX(), this.currentScan.centerY(), this.currentScan.centerZ(),
                        PROJECTION_MAT, viewMatrix, this.totalTime);
            }
        }

        PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
        PostPipeline pipeline = manager.getPipeline(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "sonar"));

        if (pipeline != null) {
            pipeline.getUniformSafe("InSize").setVector((float) VIEWPORT_SIZE, (float) VIEWPORT_SIZE);
            pipeline.getUniformSafe("Time").setFloat(this.totalTime);
        }

        PostPipeline.Context context = manager.getPostPipelineContext();
        context.setFramebuffer(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "sonar"), this.fbo);
        context.setFramebuffer(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "sonar_final"), this.finalFbo);

        manager.runPipeline(pipeline, false);
    }

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(0, 0, this.width, this.height, 0xFF0a0a0a);
    }

    @Override
    public void tick() {
        super.tick();
        boolean shouldClose = isSubLevelMode
                ? (this.subLevel == null || this.subLevel.isRemoved() || this.sonarEntity.isRemoved())
                : this.sonarEntity.isRemoved();
        if (shouldClose) {
            this.onClose();
            return;
        }
        this.totalTime += 0.05f;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean shouldClose = isSubLevelMode
                ? (this.subLevel == null || this.subLevel.isRemoved() || this.sonarEntity.isRemoved())
                : this.sonarEntity.isRemoved();
        if (shouldClose) {
            this.onClose();
            return;
        }

        this.renderScan(partialTicks);

        int vpX = this.width / 2 - VIEWPORT_SIZE / 2;
        int vpY = this.height / 2 - VIEWPORT_SIZE / 2;

        graphics.fill(vpX - 2, vpY - 2, vpX + VIEWPORT_SIZE + 2, vpY + VIEWPORT_SIZE + 2, 0xFF002200);

        if (this.finalFbo != null) {
            renderFBO(graphics, this.finalFbo, vpX, vpY, VIEWPORT_SIZE, VIEWPORT_SIZE);
        }

        if (this.currentScan == null) {
            String text = "Sonar initializing...";
            graphics.drawString(this.font, text, vpX + VIEWPORT_SIZE / 2 - this.font.width(text) / 2,
                    vpY + VIEWPORT_SIZE / 2 - 4, 0xFF00FF00);
        }

        String info = "Yaw: " + (int) this.yaw + " Pitch: " + (int) this.pitch;
        graphics.drawString(this.font, info, vpX, vpY + VIEWPORT_SIZE + 4, 0xFF004400);

        if (isSubLevelMode && this.subLevel != null && !this.subLevel.isRemoved()) {
            String name = this.subLevel.getName();
            if (name != null && !name.isEmpty()) {
                graphics.drawString(this.font, name, vpX, vpY - 12, 0xFF00FF00);
            }
        }
    }

    public static void renderFBO(GuiGraphics graphics, AdvancedFbo fbo, int x, int y, int width, int height) {
        int id = fbo.getColorTextureAttachment(0).getId();

        RenderSystem.setShaderTexture(0, id);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        Matrix4f mat = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        buffer.addVertex(mat, x, y, 0).setUv(0, 1).setColor(0xFFFFFFFF);
        buffer.addVertex(mat, x, y + height, 0).setUv(0, 0).setColor(0xFFFFFFFF);
        buffer.addVertex(mat, x + width, y + height, 0).setUv(1, 0).setColor(0xFFFFFFFF);
        buffer.addVertex(mat, x + width, y, 0).setUv(1, 1).setColor(0xFFFFFFFF);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
