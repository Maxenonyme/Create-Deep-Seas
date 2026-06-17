package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WardingEffectPayload(int playerId) implements CustomPacketPayload {
    public static final Type<WardingEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "warding_effect"));

    public static final StreamCodec<FriendlyByteBuf, WardingEffectPayload> CODEC = CustomPacketPayload.codec(
            WardingEffectPayload::write, WardingEffectPayload::new);

    public WardingEffectPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WardingEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                ClientHandler.handle(payload);
            }
        });
    }

    private static class ClientHandler {
        private static void handle(WardingEffectPayload payload) {
            com.maxenonyme.createsubmarine.submarine.client.WardingEffectHandler.trigger(payload.playerId());
        }
    }
}
