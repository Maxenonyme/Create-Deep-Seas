package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SonarConfigPayload(int entityID, float yaw, float pitch) implements CustomPacketPayload {
    public static final Type<SonarConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "sonar_config"));
    public static final StreamCodec<FriendlyByteBuf, SonarConfigPayload> CODEC = CustomPacketPayload.codec(
            SonarConfigPayload::write,
            SonarConfigPayload::new
    );

    public SonarConfigPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt(), buffer.readFloat(), buffer.readFloat());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(entityID);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
    }

    public static void handle(SonarConfigPayload payload, IPayloadContext context) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
