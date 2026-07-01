package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record ShapeVizPayload(UUID subId, String mode, boolean value) implements CustomPacketPayload {
    public static final Type<ShapeVizPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "shape_viz"));

    public static final StreamCodec<FriendlyByteBuf, ShapeVizPayload> CODEC = CustomPacketPayload.codec(
            ShapeVizPayload::write, ShapeVizPayload::new);

    public ShapeVizPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(32), buf.readBoolean());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(subId);
        buf.writeUtf(mode);
        buf.writeBoolean(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShapeVizPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CreateSubmarine.LOGGER.info("ShapeViz payload: subId={}, mode={}, value={}", payload.subId(), payload.mode(), payload.value());
        });
    }
}
