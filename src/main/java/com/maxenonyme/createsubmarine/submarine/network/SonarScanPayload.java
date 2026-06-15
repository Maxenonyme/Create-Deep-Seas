package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.sonar.SonarScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SonarScanPayload(byte[] heights, int[] colors, int gridSize, int step, int radius, int centerX, int centerY, int centerZ) implements CustomPacketPayload {
    public static final Type<SonarScanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "sonar_scan"));

    public static final StreamCodec<FriendlyByteBuf, SonarScanPayload> CODEC = new StreamCodec<>() {
        @Override
        public SonarScanPayload decode(FriendlyByteBuf buf) {
            int gridSize = buf.readVarInt();
            int step = buf.readVarInt();
            int radius = buf.readVarInt();
            int centerX = buf.readVarInt();
            int centerY = buf.readVarInt();
            int centerZ = buf.readVarInt();
            int len = gridSize * gridSize;
            byte[] heights = new byte[len];
            int[] colors = new int[len];
            for (int i = 0; i < len; i++) {
                heights[i] = buf.readByte();
                colors[i] = buf.readInt();
            }
            return new SonarScanPayload(heights, colors, gridSize, step, radius, centerX, centerY, centerZ);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SonarScanPayload value) {
            buf.writeVarInt(value.gridSize);
            buf.writeVarInt(value.step);
            buf.writeVarInt(value.radius);
            buf.writeVarInt(value.centerX);
            buf.writeVarInt(value.centerY);
            buf.writeVarInt(value.centerZ);
            for (int i = 0; i < value.heights.length; i++) {
                buf.writeByte(value.heights[i]);
                buf.writeInt(value.colors[i]);
            }
        }
    };

    public static void handle(SonarScanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof SonarScreen sonarScreen) {
                sonarScreen.updateScan(payload);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
