package com.maxenonyme.createsubmarine.submarine.network;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.sonar.SonarPingerEntity;
import com.maxenonyme.createsubmarine.submarine.sonar.SonarScreen;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SonarOpenPayload(int entityID) implements CustomPacketPayload {
    public static final Type<SonarOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "open_sonar"));
    public static final StreamCodec<FriendlyByteBuf, SonarOpenPayload> CODEC = CustomPacketPayload.codec(
            SonarOpenPayload::write,
            SonarOpenPayload::new
    );

    public SonarOpenPayload(FriendlyByteBuf buffer) {
        this(buffer.readInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(entityID);
    }

    public static void handle(SonarOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level == null) return;
            Entity entity = level.getEntity(payload.entityID());
            if (!(entity instanceof SonarPingerEntity sonar)) return;
            SubLevel subLevel = Sable.HELPER.getContaining(sonar);
            SonarScreen.open(sonar, subLevel);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
