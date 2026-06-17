package com.maxenonyme.createsubmarine.submarine.item.leak_detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.maxenonyme.createsubmarine.submarine.client.renderer.LeakDetectorItemRenderer;
import com.maxenonyme.createsubmarine.submarine.network.LeakDetectorPayload;
import com.maxenonyme.createsubmarine.submarine.system.SubmarinePressureSystem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

public class LeakDetectorItem extends Item {

    private static final int MAX_SCAN_DIST = 80;
    private static final int MAX_WAYPOINTS = 8;
    private static final int COOLDOWN = 15;

    public LeakDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        Vec3 playerPos = player.getEyePosition();

        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, BlockPos.containing(playerPos));
        if (!(sub instanceof dev.ryanhcode.sable.sublevel.SubLevel sl)) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("create_submarine.command.findhole.not_in_submarine")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        UUID subId = sl.getUniqueId();
        if (sub == null) {
            return InteractionResultHolder.fail(stack);
        }

        Set<BlockPos> breaches = SubmarinePressureSystem.getBreachedPositions(subId);
        Vec3 leakWorldPos = findClosestBreach(sub, playerPos, breaches);
        if (leakWorldPos == null) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("create_submarine.command.findhole.sealed")
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
            return InteractionResultHolder.fail(stack);
        }

        List<Vec3> path = computePath(level, playerPos, leakWorldPos);

        PacketDistributor.sendToPlayer(serverPlayer, new LeakDetectorPayload(subId, path));

        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    private Vec3 findClosestBreach(SubLevelAccess sub, Vec3 playerWorldPos, Set<BlockPos> breaches) {
        if (breaches.isEmpty()) return null;

        Vec3 best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (BlockPos bp : breaches) {
            org.joml.Vector3d worldVec = new org.joml.Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
            sub.logicalPose().transformPosition(worldVec);
            Vec3 wv = new Vec3(worldVec.x, worldVec.y, worldVec.z);
            double dsq = wv.distanceToSqr(playerWorldPos);
            if (dsq < bestDistSq && dsq < MAX_SCAN_DIST * MAX_SCAN_DIST) {
                bestDistSq = dsq;
                best = wv;
            }
        }
        return best;
    }

    private List<Vec3> computePath(Level level, Vec3 from, Vec3 to) {
        List<Vec3> waypoints = new ArrayList<>();
        waypoints.add(from);

        Vec3 current = from;
        Vec3 target = to;
        int iterations = 0;

        while (iterations < MAX_WAYPOINTS) {
            ClipContext ctx = new ClipContext(current, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
            BlockHitResult hit = level.clip(ctx);

            if (hit.getType() == HitResult.Type.MISS) {
                break;
            }

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = hit.getBlockPos();
                Vec3 hitLoc = hit.getLocation();

                Vec3 dir = target.subtract(current).normalize();
                Vec3 away = findAirNeighbor(level, hitPos, target, current);
                if (away == null) {
                    waypoints.add(hitLoc);
                    break;
                }
                waypoints.add(away);
                current = away;
                iterations++;
            }
        }

        waypoints.add(target);
        return waypoints;
    }

    private Vec3 findAirNeighbor(Level level, BlockPos obstacle, Vec3 target, Vec3 origin) {
        Vec3 dir = target.subtract(origin).normalize();
        Vec3[] candidates = {
            new Vec3(0, 1, 0),
            new Vec3(0, -1, 0),
            new Vec3(1, 0, 0),
            new Vec3(-1, 0, 0),
            new Vec3(0, 0, 1),
            new Vec3(0, 0, -1),
            new Vec3(1, 1, 0),
            new Vec3(-1, 1, 0),
            new Vec3(0, 1, 1),
            new Vec3(0, 1, -1),
            new Vec3(1, -1, 0),
            new Vec3(-1, -1, 0),
        };

        Vec3 best = null;
        double bestDot = -2;

        for (Vec3 cand : candidates) {
            BlockPos checkPos = obstacle.offset((int) Math.round(cand.x), (int) Math.round(cand.y), (int) Math.round(cand.z));
            if (level.getBlockState(checkPos).isAir() || level.getBlockState(checkPos).canBeReplaced()) {
                Vec3 candWorld = new Vec3(checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
                Vec3 toTarget = target.subtract(candWorld).normalize();
                double dot = dir.dot(toTarget);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = candWorld;
                }
            }
        }

        return best;
    }

    @Override
    public void initializeClient(Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new LeakDetectorItemRenderer()));
    }
}
