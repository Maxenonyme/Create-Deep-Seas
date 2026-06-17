package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.client.LeakDetectorPathRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LeakDetectorPayload(UUID subId, List<Vec3> waypoints) implements CustomPacketPayload {
    public static final Type<LeakDetectorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "leak_detector"));

    public static final StreamCodec<FriendlyByteBuf, LeakDetectorPayload> CODEC = CustomPacketPayload.codec(
            LeakDetectorPayload::write, LeakDetectorPayload::new);

    public LeakDetectorPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), readVec3List(buf));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(subId);
        buf.writeVarInt(waypoints.size());
        for (Vec3 v : waypoints) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
    }

    private static List<Vec3> readVec3List(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Vec3> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LeakDetectorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LeakDetectorPathRenderer.setPath(payload.subId(), payload.waypoints());
        });
    }
}
