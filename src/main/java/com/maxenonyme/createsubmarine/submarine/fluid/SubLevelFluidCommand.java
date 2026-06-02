package com.maxenonyme.createsubmarine.submarine.fluid;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

public final class SubLevelFluidCommand {

    private SubLevelFluidCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("dsfluid").requires(src -> src.hasPermission(2))
                .then(Commands.literal("add")
                    .then(Commands.argument("mb", IntegerArgumentType.integer(1, 64000))
                        .executes(ctx -> add(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(ctx, "mb")))))
                .then(Commands.literal("clear")
                    .executes(ctx -> clear(ctx.getSource(),
                            ctx.getSource().getPlayerOrException())))
        );
    }

    private static int add(CommandSourceStack src, ServerPlayer player, int mb) {
        SubLevel sub = nearest(player);
        if (sub == null) {
            src.sendFailure(Component.literal("No sublevel found nearby."));
            return 0;
        }
        BlockPos local = localCell(player, sub);

        net.minecraft.world.level.LevelAccessor lvl = SubLevelLookup.embeddedLevel(sub);
        if (lvl == null) {
            src.sendFailure(Component.literal("Sublevel has no embedded level (plot null)."));
            return 0;
        }
        net.minecraft.world.level.block.state.BlockState before = lvl.getBlockState(local);
        boolean placed = lvl.setBlock(local, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 3);
        net.minecraft.world.level.block.state.BlockState after = lvl.getBlockState(local);
        src.sendSuccess(() -> Component.literal(
                "local=" + local.toShortString()
                + " before=" + before.getBlock().getName().getString()
                + " setBlock=" + placed
                + " after=" + after.getBlock().getName().getString()), false);

        int now = SubLevelFluidApi.addWater(sub.getUniqueId(), local, mb);
        src.sendSuccess(() -> Component.literal("Store now holds " + now + " mB at that cell"), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, ServerPlayer player) {
        SubLevel sub = nearest(player);
        if (sub == null) {
            src.sendFailure(Component.literal("No sublevel found nearby."));
            return 0;
        }
        SubLevelFluidSimulation.purge(sub);
        src.sendSuccess(() -> Component.literal("Cleared sublevel fluid store."), false);
        return 1;
    }

    private static SubLevel nearest(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return SubLevelLookup.nearest(player.server, eye.x, eye.y, eye.z);
    }

    private static BlockPos localCell(ServerPlayer player, SubLevel sub) {
        Vec3 eye = player.getEyePosition();
        Vector3d local = new Vector3d(eye.x, eye.y, eye.z);
        sub.logicalPose().transformPositionInverse(local);
        return BlockPos.containing(local.x, local.y, local.z);
    }
}
