package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.compartment.CompartmentTracker;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

import java.util.UUID;

public final class SubmarineInfoCommand {
    private SubmarineInfoCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("submarine")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info").executes(SubmarineInfoCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }
        Level level = player.level();
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            source.sendFailure(Component.literal("No sublevels in this dimension."));
            return 0;
        }

        Vector3d ppos = new Vector3d(player.getX(), player.getY(), player.getZ());
        SubLevel found = null;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (sub.getPlot() == null) continue;
            BoundingBox3ic b = sub.getPlot().getBoundingBox();
            if (b == null) continue;
            Vector3d local = new Vector3d(ppos);
            try {
                sub.logicalPose().transformPositionInverse(local);
            } catch (Throwable t) {
                continue;
            }
            if (local.x >= b.minX() - 2 && local.x <= b.maxX() + 2
                    && local.y >= b.minY() - 2 && local.y <= b.maxY() + 2
                    && local.z >= b.minZ() - 2 && local.z <= b.maxZ() + 2) {
                found = sub;
                break;
            }
        }

        if (found == null) {
            source.sendSuccess(() -> Component.literal("You are not in or on a sublevel.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        UUID id = found.getUniqueId();
        BoundingBox3ic b = found.getPlot().getBoundingBox();
        Level subLevel = found.getLevel();

        int controllers = 0, diffusers = 0, floaters = 0;
        long volume = (long) (b.maxX() - b.minX() + 1) * (b.maxY() - b.minY() + 1) * (b.maxZ() - b.minZ() + 1);
        boolean scanned = subLevel != null && volume <= 50_000;
        if (scanned) {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        m.set(x, y, z);
                        BlockState s = subLevel.getBlockState(m);
                        if (s.is(CreateSubmarine.CREATIVE_OXYGENATOR.get())) controllers++;
                        else if (s.is(CreateSubmarine.OXYGENE_DIFFUSER.get())) diffusers++;
                        else if (s.is(CreateSubmarine.FLOATER.get())) floaters++;
                    }
                }
            }
        }

        boolean hermetic = CompartmentTracker.hasAnySealed(id);
        boolean breached = SubmarinePressureSystem.isBreached(id);
        int cracks = SubmarinePressureSystem.getCrackCount(id);
        int depth = SubmarinePressureSystem.getCachedDepth(id);
        boolean underPressure = hermetic && depth > 0 && cracks > 0;

        final int fc = controllers, fd = diffusers, ff = floaters;
        final boolean fScanned = scanned;
        source.sendSuccess(() -> Component.literal("=== Submarine Info ===").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> line("Sublevel", id.toString().substring(0, 8)), false);
        if (fScanned) {
            source.sendSuccess(() -> line("Hull controllers", String.valueOf(fc)), false);
            source.sendSuccess(() -> line("Oxygen diffusers", String.valueOf(fd)), false);
            source.sendSuccess(() -> line("Floaters", String.valueOf(ff)), false);
        } else {
            source.sendSuccess(() -> Component.literal("(too large to scan modules)").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        source.sendSuccess(() -> bool("Hermetic (sealed)", hermetic), false);
        source.sendSuccess(() -> bool("Breached", breached), false);
        source.sendSuccess(() -> line("Cracked blocks", String.valueOf(cracks)), false);
        source.sendSuccess(() -> line("Water depth", String.valueOf(depth)), false);
        source.sendSuccess(() -> bool("Under pressure", underPressure), false);
        return 1;
    }

    private static Component line(String label, String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static Component bool(String label, boolean value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value ? "yes" : "no").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
