package com.maxenonyme.createsubmarine.submarine.system;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SubmarineAbyssCommand {
    private SubmarineAbyssCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("submarine")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("abyss").executes(SubmarineAbyssCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Player only."));
            return 0;
        }

        ResourceKey<Level> abyssKey = ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("create_submarine", "abyss"));
        ServerLevel abyss = player.getServer().getLevel(abyssKey);
        if (abyss == null) {
            source.sendFailure(Component.literal("Abyss dimension not found."));
            return 0;
        }

        int range = 5000;
        int x = player.getRandom().nextInt(range * 2) - range;
        int z = player.getRandom().nextInt(range * 2) - range;

        int topY = 699;
        int minY = 1;
        int seaFloorY = -1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, 0, z);
        for (int y = topY; y >= minY; y--) {
            cursor.setY(y);
            BlockState state = abyss.getBlockState(cursor);
            if (!state.isAir() && !state.liquid()) {
                seaFloorY = y;
                break;
            }
        }

        if (seaFloorY == -1) {
            source.sendFailure(Component.literal("Could not find sea floor at this location. Try again."));
            return 0;
        }

        int fx = x;
        int fz = z;
        int fy = seaFloorY + 1;
        player.teleportTo(abyss, fx + 0.5, fy, fz + 0.5, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal(
                "Teleported to Abyss sea floor at " + fx + " " + fy + " " + fz), true);
        return 1;
    }
}
