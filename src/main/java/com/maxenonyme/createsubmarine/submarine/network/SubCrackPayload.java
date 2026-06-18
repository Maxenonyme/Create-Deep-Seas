package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SubCrackPayload(UUID subId, BlockPos plotPos, int crackLevel, int blockId) implements CustomPacketPayload {
    public static final Type<SubCrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "sub_crack"));

    public static final StreamCodec<FriendlyByteBuf, SubCrackPayload> CODEC = CustomPacketPayload.codec(
            SubCrackPayload::write, SubCrackPayload::new);

    public SubCrackPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readBlockPos(), buf.readInt(), buf.readInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(subId);
        buf.writeBlockPos(plotPos);
        buf.writeInt(crackLevel);
        buf.writeInt(blockId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubCrackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                ClientHandler.handle(payload);
            }
        });
    }

    private static class ClientHandler {
        private static void handle(SubCrackPayload payload) {
            com.maxenonyme.createsubmarine.submarine.client.SubLevelCrackRenderer.updateCrack(
                    payload.subId(), payload.plotPos(), payload.crackLevel(), payload.blockId());
        }
    }
}
