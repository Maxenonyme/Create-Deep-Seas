package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record HullConfigSyncPayload(Map<String, HullStrengthConfig.HullProperty> values) implements CustomPacketPayload {
    public static final Type<HullConfigSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "hull_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, HullConfigSyncPayload> CODEC = StreamCodec.of(
        HullConfigSyncPayload::write,
        HullConfigSyncPayload::read
    );

    private static void write(FriendlyByteBuf buf, HullConfigSyncPayload payload) {
        buf.writeVarInt(payload.values.size());
        for (Map.Entry<String, HullStrengthConfig.HullProperty> e : payload.values.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().maxWaterDepth());
            buf.writeFloat(e.getValue().implosionChance());
        }
    }

    private static HullConfigSyncPayload read(FriendlyByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readVarInt(), 100000));
        Map<String, HullStrengthConfig.HullProperty> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(256);
            int depth = buf.readVarInt();
            float chance = buf.readFloat();
            map.put(key, new HullStrengthConfig.HullProperty(depth, chance));
        }
        return new HullConfigSyncPayload(map);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final HullConfigSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> HullStrengthConfig.applySynced(payload.values()));
    }
}
