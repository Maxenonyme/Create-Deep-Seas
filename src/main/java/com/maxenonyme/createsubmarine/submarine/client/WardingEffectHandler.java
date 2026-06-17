package com.maxenonyme.createsubmarine.submarine.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleTypes;
import team.lodestar.lodestone.systems.easing.Easing;
import team.lodestar.lodestone.systems.particle.builder.WorldParticleBuilder;
import team.lodestar.lodestone.systems.particle.data.GenericParticleData;
import team.lodestar.lodestone.systems.particle.data.color.ColorParticleData;
import team.lodestar.lodestone.systems.particle.data.spin.SpinParticleData;

public final class WardingEffectHandler {
    private static final int EFFECT_DURATION = 40;

    private static int activeTicks = 0;
    private static int targetPlayerId = -1;
    private static PointLightData lightData;
    private static LightRenderHandle lightHandle;
    private static boolean registered = false;

    private WardingEffectHandler() {
    }

    public static void trigger(int playerId) {
        targetPlayerId = playerId;
        activeTicks = EFFECT_DURATION;
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, WardingEffectHandler::onClientTick);
        }
    }

    private static void onClientTick(ClientTickEvent.Pre event) {
        if (activeTicks <= 0) {
            cleanup();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = (Player) (level != null ? level.getEntity(targetPlayerId) : null);
        if (player == null) {
            cleanup();
            return;
        }

        double px = player.getX();
        double py = player.getY() + 1.0;
        double pz = player.getZ();

        if (lightData == null) {
            lightData = new PointLightData();
            lightData.setColor(0.6F, 0.8F, 1.0F);
            lightData.setBrightness(12.0F);
            lightData.setOcclusionEnabled(false);
        }

        float rad = Math.min(20.0F - (20.0F - activeTicks * 0.5F), 20.0F);
        lightData.setPosition((float) px, (float) py, (float) pz);
        lightData.setRadius(rad);
        if (lightHandle == null && VeilRenderSystem.renderer() != null) {
            lightHandle = VeilRenderSystem.renderer().getLightRenderer().addLight(lightData);
        }

        float progress = 1.0F - ((float) activeTicks / EFFECT_DURATION);

        for (int ring = 0; ring < 4; ring++) {
            float phase = (progress + (float) ring / 4) % 1.0F;
            double radius = 2.0 + phase * 12.0;
            int count = 8 + (int) (phase * 24);
            double yOff = Math.sin(phase * Math.PI * 2) * 1.5;
            for (int i = 0; i < count; i++) {
                double angle = (i / (double) count) * Math.PI * 2 + activeTicks * 0.15;
                double x = px + Math.cos(angle) * radius;
                double z = pz + Math.sin(angle) * radius;
                double y = py + yOff;

                float age = (float) i / count;
                if (ring % 2 == 0) {
                    WorldParticleBuilder.create(LodestoneParticleTypes.WISP_PARTICLE.get())
                        .setColorData(ColorParticleData.create(0.5f, 0.7f, 1.0f, 1.0f, 1.0f, 1.0f).setEasing(Easing.SINE_IN_OUT).build())
                        .setTransparencyData(GenericParticleData.create(0.8f, 0f).setEasing(Easing.SINE_IN_OUT).build())
                        .setScaleData(GenericParticleData.create(0.4f, 0f).build())
                        .setSpinData(SpinParticleData.create(0.2f).build())
                        .setLifetime(25)
                        .setGravity(0f)
                        .enableNoClip()
                        .spawn(level, x, y, z);
                } else {
                    WorldParticleBuilder.create(LodestoneParticleTypes.SMOKE_PARTICLE.get())
                        .setColorData(ColorParticleData.create(0.4f, 0.6f, 1.0f, 0.8f, 0.9f, 1.0f).setEasing(Easing.SINE_IN_OUT).build())
                        .setTransparencyData(GenericParticleData.create(0.6f, 0f).setEasing(Easing.SINE_IN_OUT).build())
                        .setScaleData(GenericParticleData.create(0.5f, 0f).build())
                        .setSpinData(SpinParticleData.create(0.15f).build())
                        .setLifetime(30)
                        .setGravity(0f)
                        .enableNoClip()
                        .spawn(level, x, y, z);
                }
            }
        }

        if (activeTicks > EFFECT_DURATION - 8) {
            for (int i = 0; i < 3; i++) {
                double a = player.getRandom().nextDouble() * Math.PI * 2;
                double r = 0.5 + player.getRandom().nextDouble() * 1.5;
                WorldParticleBuilder.create(LodestoneParticleTypes.SPARKLE_PARTICLE.get())
                    .setColorData(ColorParticleData.create(0.6f, 0.8f, 1.0f, 1.0f, 1.0f, 1.0f).build())
                    .setTransparencyData(GenericParticleData.create(0.7f, 0f).setEasing(Easing.SINE_IN_OUT).build())
                    .setScaleData(GenericParticleData.create(0.3f, 0f).build())
                    .setLifetime(15)
                    .setGravity(0f)
                    .enableNoClip()
                    .spawn(level, px + Math.cos(a) * r, py + 0.5, pz + Math.sin(a) * r);
            }
        }

        activeTicks--;
    }

    private static void cleanup() {
        activeTicks = 0;
        targetPlayerId = -1;
        if (lightHandle != null) {
            lightHandle.free();
            lightHandle = null;
        }
    }

    public static void clearOnLogout() {
        cleanup();
    }
}
