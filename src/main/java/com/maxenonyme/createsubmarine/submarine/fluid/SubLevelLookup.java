package com.maxenonyme.createsubmarine.submarine.fluid;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

import java.util.UUID;
import java.util.function.Consumer;

public final class SubLevelLookup {

    private SubLevelLookup() {}

    public static void forEach(MinecraftServer server, Consumer<SubLevel> action) {
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) continue;
            for (SubLevel sub : container.getAllSubLevels()) {
                if (sub != null && !sub.isRemoved()) {
                    action.accept(sub);
                }
            }
        }
    }

    public static SubLevel byId(MinecraftServer server, UUID id) {
        SubLevel[] found = new SubLevel[1];
        forEach(server, sub -> {
            if (found[0] == null && id.equals(sub.getUniqueId())) found[0] = sub;
        });
        return found[0];
    }

    public static SubLevel nearest(MinecraftServer server, double wx, double wy, double wz) {
        SubLevel[] best = new SubLevel[1];
        double[] bestDist = {Double.MAX_VALUE};
        forEach(server, sub -> {
            org.joml.Vector3dc p = sub.logicalPose().position();
            double dx = p.x() - wx, dy = p.y() - wy, dz = p.z() - wz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist[0]) {
                bestDist[0] = d;
                best[0] = sub;
            }
        });
        return best[0];
    }

    public static LevelAccessor embeddedLevel(SubLevel sub) {
        dev.ryanhcode.sable.sublevel.plot.LevelPlot plot = sub.getPlot();
        return plot == null ? null : plot.getEmbeddedLevelAccessor();
    }
}
