package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.ShapeVizRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record StressCenterPayload(UUID subId, BlockPos worldPos) implements CustomPacketPayload {
    public static final Type<StressCenterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "stress_center"));

    public static final StreamCodec<FriendlyByteBuf, StressCenterPayload> CODEC = CustomPacketPayload.codec(
            StressCenterPayload::write, StressCenterPayload::new);

    public StressCenterPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readBlockPos());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(subId);
        buf.writeBlockPos(worldPos);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StressCenterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CreateSubmarine.LOGGER.debug("StressCenterPayload: subId={}, pos={}",
                payload.subId().toString().substring(0, 8), payload.worldPos().toShortString());
            ShapeVizRenderer.setStressCenter(payload.subId(), payload.worldPos());
        });
    }
}
